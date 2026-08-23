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

// Le fond de carte est optionnel et n'a pas de valeur par défaut : aucun
// serveur de tuiles n'est choisi dans le dépôt, parce que ce choix engage une
// licence et parfois un compte. Vide, la carte reste sur fond uni.
val tileUrlTemplate =
    secret("MADHI_TILE_URL_TEMPLATE", "madhi.tiles.urlTemplate", localProperties) ?: ""

val tileAttribution =
    secret("MADHI_TILE_ATTRIBUTION", "madhi.tiles.attribution", localProperties) ?: ""

// Dernier niveau de zoom servi par la source. 19 est le defaut des rendus
// complets d'OpenStreetMap ; un fond auto-heberge s'arrete bien plus bas.
val tileMaxZoom =
    secret("MADHI_TILE_MAX_ZOOM", "madhi.tiles.maxZoom", localProperties)?.toIntOrNull() ?: 19

android {
    namespace = "com.madhi.tracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.madhi.tracker"

        // Android 10 : première version où ACCESS_BACKGROUND_LOCATION et
        // foregroundServiceType existent nativement. En dessous, le code
        // devrait porter des branches héritées pour un cas qui ne se
        // présentera jamais — l'appareil du voyage est sous Android 13.
        minSdk = 29

        // Android 14 volontairement, pas la dernière version. L'appareil
        // cible tourne sous Android 13 : viser plus haut ne change rien à
        // son comportement, mais ferait hériter du durcissement des quotas
        // de jobs d'Android 16 si l'appareil était remplacé en cours de
        // voyage. Voir arch/adr/007-contraintes-miui-redmi-note-11.md §3.5.
        targetSdk = 34

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
            buildConfigField("String", "TILE_URL_TEMPLATE", "\"$tileUrlTemplate\"")
            buildConfigField("String", "TILE_ATTRIBUTION", "\"$tileAttribution\"")
            buildConfigField("int", "TILE_MAX_ZOOM", "$tileMaxZoom")
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
            buildConfigField("String", "TILE_URL_TEMPLATE", "\"$tileUrlTemplate\"")
            buildConfigField("String", "TILE_ATTRIBUTION", "\"$tileAttribution\"")
            buildConfigField("int", "TILE_MAX_ZOOM", "$tileMaxZoom")
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

    testOptions {
        unitTests {
            // Room et DataStore ont besoin d'un vrai Context et des ressources
            // de l'application pour tourner sous Robolectric.
            isIncludeAndroidResources = true
        }
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
    // Copie locale volontaire : lire `releaseApiBaseUrl` depuis `doLast` capturerait
    // le script de build lui-même, que le cache de configuration ne sait pas
    // sérialiser — et le build release échouait pour cette seule raison.
    val apiBaseUrl = releaseApiBaseUrl
    doLast {
        check(!apiBaseUrl.contains("example.invalid")) {
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
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

/**
 * Garde-fou architectural exécuté à chaque `check`.
 *
 * La direction des dépendances est la seule chose qui garantit que le cœur
 * métier reste testable sans émulateur. Une revue de code la laisse filer
 * tôt ou tard ; une tâche Gradle, non.
 */
val checkCoreIsFrameworkFree by tasks.registering {
    group = "verification"
    description = "Vérifie que domain/ et application/ n'importent aucun framework."

    val coreSources = listOf(
        file("src/main/java/com/madhi/tracker/domain"),
        file("src/main/java/com/madhi/tracker/application"),
    )
    val forbidden = listOf(
        "import android.",
        "import androidx.",
        "import dagger.",
        "import okhttp3.",
        "import retrofit2.",
        "import com.google.",
    )

    // Capturé à la configuration : le cache de configuration interdit de
    // référencer le projet depuis l'exécution de la tâche.
    val moduleDirectory = projectDir

    inputs.files(coreSources.map { fileTree(it) })
    outputs.upToDateWhen { true }

    doLast {
        val violations = coreSources
            .filter { it.exists() }
            .flatMap { root -> root.walkTopDown().filter { it.extension == "kt" } }
            .flatMap { source ->
                source.readLines().withIndex().mapNotNull { (index, line) ->
                    forbidden.find { line.trimStart().startsWith(it) }
                        ?.let { "${source.relativeTo(moduleDirectory)}:${index + 1}  $line" }
                }
            }

        check(violations.isEmpty()) {
            buildString {
                appendLine("Le cœur métier importe un framework :")
                violations.forEach { appendLine("  $it") }
                appendLine()
                appendLine("Le domaine et les use cases doivent rester compilables et")
                appendLine("testables sans Android. Passez par un port.")
            }
        }
    }
}

tasks.named("check") { dependsOn(checkCoreIsFrameworkFree) }
