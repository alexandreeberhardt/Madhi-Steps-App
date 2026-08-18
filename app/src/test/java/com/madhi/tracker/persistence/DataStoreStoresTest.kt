package com.madhi.tracker.persistence

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.madhi.tracker.adapter.output.persistence.datastore.DataStoreRebootJournalStore
import com.madhi.tracker.adapter.output.persistence.datastore.DataStoreSyncJournalStore
import com.madhi.tracker.adapter.output.persistence.datastore.DataStoreTrackingIntentStore
import com.madhi.tracker.adapter.output.persistence.datastore.TrackerPreferences
import com.madhi.tracker.domain.error.SyncFailure
import com.madhi.tracker.domain.model.CaptureInterval
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import kotlin.time.Duration.Companion.hours

@RunWith(AndroidJUnit4::class)
// Le telephone cible tourne sous Android 13.
@Config(sdk = [33])
class DataStoreStoresTest {

    private lateinit var file: File
    private lateinit var dataStore: DataStore<Preferences>

    private val now: Instant = Instant.parse("2026-08-18T12:00:00Z")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        file = File(context.filesDir, "test-${System.nanoTime()}.preferences_pb")
        dataStore = PreferenceDataStoreFactory.create { file }
    }

    @After
    fun tearDown() {
        file.delete()
    }

    // --- Intention de suivi

    @Test
    fun le_suivi_est_arrete_par_defaut_et_l_intervalle_vaut_cinq_minutes() = runTest {
        val intent = DataStoreTrackingIntentStore(dataStore).read()

        assertFalse(intent.enabled)
        assertEquals(CaptureInterval.FIVE, intent.captureInterval)
    }

    @Test
    fun l_intention_survit_a_une_nouvelle_instance_du_store() = runTest {
        DataStoreTrackingIntentStore(dataStore).apply {
            setEnabled(true)
            setCaptureInterval(CaptureInterval.FIFTEEN)
        }

        // Simule une relecture après redémarrage du processus.
        val reread = DataStoreTrackingIntentStore(dataStore).read()

        assertTrue(reread.enabled)
        assertEquals(CaptureInterval.FIFTEEN, reread.captureInterval)
    }

    @Test
    fun changer_l_intervalle_ne_touche_pas_a_l_activation() = runTest {
        val store = DataStoreTrackingIntentStore(dataStore)
        store.setEnabled(true)

        store.setCaptureInterval(CaptureInterval.THIRTY)

        assertTrue(store.read().enabled)
    }

    @Test
    fun un_intervalle_persiste_inconnu_retombe_sur_le_defaut() = runTest {
        // Cas d'une évolution de la liste des paliers entre deux versions.
        dataStore.edit { it[TrackerPreferences.CAPTURE_INTERVAL_MINUTES] = 7 }

        assertEquals(CaptureInterval.DEFAULT, DataStoreTrackingIntentStore(dataStore).read().captureInterval)
    }

    // --- Journal de synchronisation

    @Test
    fun le_journal_de_synchronisation_part_vide() = runTest {
        val journal = DataStoreSyncJournalStore(dataStore).read()

        assertNull(journal.lastAttemptAt)
        assertNull(journal.lastSuccessAt)
        assertEquals(0, journal.consecutiveFailures)
    }

    @Test
    fun les_echecs_consecutifs_s_accumulent() = runTest {
        val store = DataStoreSyncJournalStore(dataStore)

        store.recordFailure(now, SyncFailure.Timeout)
        store.recordFailure(now.plusSeconds(60), SyncFailure.ServerError(503))

        val journal = store.read()
        assertEquals(2, journal.consecutiveFailures)
        assertEquals("server_error_503", journal.lastFailureCode)
    }

    @Test
    fun un_succes_remet_le_compteur_d_echecs_a_zero_et_efface_la_derniere_erreur() = runTest {
        val store = DataStoreSyncJournalStore(dataStore)
        store.recordFailure(now, SyncFailure.Timeout)
        store.recordFailure(now.plusSeconds(60), SyncFailure.NoNetwork)

        store.recordSuccess(now.plusSeconds(120))

        val journal = store.read()
        assertEquals(0, journal.consecutiveFailures)
        assertNull(journal.lastFailureCode)
        assertEquals(now.plusSeconds(120), journal.lastSuccessAt)
    }

    @Test
    fun une_tentative_enregistre_sa_date_et_la_taille_du_lot() = runTest {
        val store = DataStoreSyncJournalStore(dataStore)

        store.recordAttempt(now, batchSize = 200)

        val journal = store.read()
        assertEquals(now, journal.lastAttemptAt)
        assertEquals(200, journal.lastBatchSize)
    }

    @Test
    fun un_succes_ne_fait_pas_oublier_la_date_du_dernier_essai() = runTest {
        val store = DataStoreSyncJournalStore(dataStore)
        store.recordAttempt(now, batchSize = 10)

        store.recordSuccess(now.plusSeconds(5))

        assertEquals(now, store.read().lastAttemptAt)
    }

    // --- Journal de redémarrage

    @Test
    fun le_journal_de_redemarrage_part_vide() = runTest {
        val journal = DataStoreRebootJournalStore(dataStore).read()

        assertNull(journal.lastSeenAt)
        assertNull(journal.lastSeenUptime)
        assertNull(journal.bootHandledAt)
    }

    @Test
    fun un_signe_de_vie_enregistre_l_heure_et_le_temps_de_fonctionnement() = runTest {
        val store = DataStoreRebootJournalStore(dataStore)

        store.recordAlive(now, uptime = 5.hours)

        val journal = store.read()
        assertEquals(now, journal.lastSeenAt)
        assertEquals(5.hours, journal.lastSeenUptime)
    }

    @Test
    fun le_traitement_du_demarrage_n_efface_pas_le_dernier_signe_de_vie() = runTest {
        val store = DataStoreRebootJournalStore(dataStore)
        store.recordAlive(now, uptime = 5.hours)

        store.recordBootHandled(now.plusSeconds(3600))

        val journal = store.read()
        assertEquals(now, journal.lastSeenAt)
        assertEquals(now.plusSeconds(3600), journal.bootHandledAt)
    }
}
