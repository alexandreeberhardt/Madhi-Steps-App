import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Lit une valeur de configuration sensible sans jamais la faire entrer dans Git.
 * Ordre : variable d'environnement, puis fichier local non versionné, puis défaut.
 * L'environnement l'emporte pour que la CI n'ait besoin d'aucun fichier.
 */
fun localConfig(fileName: String): Properties =
    Properties().apply {
        val file = rootProject.file(fileName)
        if (file.exists()) file.inputStream().use(::load)
    }

val localProperties = localConfig("local.properties")
val keystoreProperties = localConfig("keystore.properties")

fun secret(envName: String, propertyName: String, properties: Properties): String? =
    providers.environmentVariable(envName).orNull ?: properties.getProperty(propertyName)

val debugApiBaseUrl =
    secret("MADHI_API_BASE_URL_DEBUG", "madhi.api.baseUrl.debug", localProperties)
        ?: "http://10.0.2.2:8080/api/v1"

val releaseApiBaseUrl =
    secret("MADHI_API_BASE_URL_RELEASE", "madhi.api.baseUrl.release", localProperties)
        ?: "https://example.invalid/api/v1"

android {
    namespace = "com.madhi.tracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.madhi.tracker"

        // Android 8 : borne basse du projet. Le téléphone cible est sous
        // Android 13 ; les branches héritées restent limitées.
        minSdk = 26

        // Dernière cible stable retenue pour ce bootstrap. Le téléphone cible
        // tourne sous Android 13, mais le projet doit rester sain sur un
        // appareil de remplacement récent.
        targetSdk = 36

        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val store = secret("ANDROID_SIGNING_STORE_FILE", "storeFile", keystoreProperties)
            // Sans matériel de signature, on ne configure rien : le build release
            // échouera explicitement plutôt que de produire un APK debug-signé
            // que l'on croirait distribuable.
            if (store != null) {
                storeFile = file(store)
                storePassword = secret("ANDROID_SIGNING_STORE_PASSWORD", "storePassword", keystoreProperties)
                keyAlias = secret("ANDROID_SIGNING_KEY_ALIAS", "keyAlias", keystoreProperties)
                keyPassword = secret("ANDROID_SIGNING_KEY_PASSWORD", "keyPassword", keystoreProperties)
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"$debugApiBaseUrl\"",
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"$releaseApiBaseUrl\"",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // Schémas Room versionnés et commités : indispensable pour écrire et tester
    // les migrations. Voir arch/adr/005-retention-locale-et-migrations.md.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

val validateReleaseConfig = tasks.register("validateReleaseConfig") {
    doLast {
        check(!releaseApiBaseUrl.contains("example.invalid")) {
            "Build release sans API_BASE_URL. Renseignez MADHI_API_BASE_URL_RELEASE " +
                "ou madhi.api.baseUrl.release dans local.properties."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseConfig)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime.ktx)
    implementation(libs.datastore.preferences)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
