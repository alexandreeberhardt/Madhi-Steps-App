package com.madhi.tracker.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.madhi.tracker.adapter.output.persistence.credentials.KeystoreDeviceCredentials
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class KeystoreDeviceCredentialsTest {

    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        file = File(context.filesDir, "credentials-${System.nanoTime()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create { file }
    }

    @After
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `un token corrompu rend l'appareil non active plutot que de planter la synchronisation`() = runTest {
        val credentials = KeystoreDeviceCredentials(dataStore)
        dataStore.edit {
            it[DEVICE_ID] = "device-1"
            it[TRIP_ID] = "trip-1"
            it[DEVICE_TOKEN_ENCRYPTED] = "donnee-corrompue"
        }

        assertNull(credentials.authorizationHeaderValue())
        assertFalse(credentials.isActivated())
        assertTrue(credentials.deviceId() != null)
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val TRIP_ID = stringPreferencesKey("trip_id")
        val DEVICE_TOKEN_ENCRYPTED = stringPreferencesKey("device_token_encrypted")
    }
}
