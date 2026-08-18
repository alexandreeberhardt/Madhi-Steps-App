package com.madhi.tracker.adapter.output.persistence.credentials

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.madhi.tracker.application.port.DeviceCredentials
import com.madhi.tracker.domain.model.DeviceActivation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Le token appareil autorise l'écriture de positions dans le voyage : il ne
 * doit pas être lisible en clair par une extraction de sauvegarde.
 *
 * Il est chiffré en AES-GCM avec une clé détenue par l'Android Keystore, non
 * extractible du matériel. `androidx.security:security-crypto` aurait fait
 * la même chose, mais la bibliothèque est dépréciée et ce projet doit tenir
 * un an sans surprise (ADR-004).
 */
@Singleton
class KeystoreDeviceCredentials @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DeviceCredentials {

    override suspend fun store(activation: DeviceActivation) {
        dataStore.edit {
            it[DEVICE_ID] = activation.deviceId
            it[TRIP_ID] = activation.tripId
            it[ENCRYPTED_TOKEN] = encrypt(activation.deviceToken)
        }
    }

    override suspend fun isActivated(): Boolean = read(ENCRYPTED_TOKEN) != null

    override suspend fun deviceId(): String? = read(DEVICE_ID)

    override suspend fun tripId(): String? = read(TRIP_ID)

    override suspend fun authorizationHeaderValue(): String? =
        read(ENCRYPTED_TOKEN)?.let { "Bearer ${decrypt(it)}" }

    private suspend fun read(key: Preferences.Key<String>): String? =
        dataStore.data.map { it[key] }.first()

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        // Le vecteur d'initialisation est stocké devant le chiffré : il n'est
        // pas secret, mais il doit être unique et retrouvé au déchiffrement.
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = try {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_BYTES),
        )
        String(cipher.doFinal(bytes, GCM_IV_BYTES, bytes.size - GCM_IV_BYTES), Charsets.UTF_8)
    } catch (e: Exception) {
        // Clé perdue ou donnée corrompue : l'appareil doit être réactivé.
        // Renvoyer null vaut mieux que planter au milieu d'une synchronisation.
        null
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Surtout pas setUserAuthenticationRequired : la
                // synchronisation doit fonctionner téléphone verrouillé.
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "madhi-device-token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128

        val DEVICE_ID = stringPreferencesKey("device_id")
        val TRIP_ID = stringPreferencesKey("trip_id")
        val ENCRYPTED_TOKEN = stringPreferencesKey("device_token_encrypted")
    }
}
