package com.madhi.tracker.persistence

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.madhi.tracker.adapter.output.persistence.room.LocationEntity
import com.madhi.tracker.adapter.output.persistence.room.RoomLocationStore
import com.madhi.tracker.adapter.output.persistence.room.TrackerDatabase
import com.madhi.tracker.domain.model.Coordinates
import com.madhi.tracker.domain.model.LocationId
import com.madhi.tracker.domain.model.LocationPoint
import com.madhi.tracker.domain.model.SyncState
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
// Le telephone cible tourne sous Android 13.
@Config(sdk = [33])
class RoomLocationStoreTest {

    private lateinit var database: TrackerDatabase
    private lateinit var store: RoomLocationStore

    private val baseTime: Instant = Instant.parse("2026-08-18T08:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TrackerDatabase::class.java,
        ).build()
        store = RoomLocationStore(database.locationDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun pointAt(minutesFromBase: Long, id: LocationId = LocationId.random()) = LocationPoint(
        id = id,
        coordinates = Coordinates(48.85 + minutesFromBase / 1000.0, 2.29),
        recordedAt = baseTime.plusSeconds(minutesFromBase * 60),
        accuracyMeters = 12f,
        batteryPercent = 62,
    )

    @Test
    fun sauvegarde_puis_relit_un_point_a_l_identique() = runTest {
        val point = pointAt(0)
        store.save(point)

        val stored = store.oldestPending(limit = 10).single()

        assertEquals(point.id, stored.id)
        assertEquals(point.coordinates, stored.coordinates)
        assertEquals(point.recordedAt, stored.recordedAt)
        assertEquals(point.accuracyMeters, stored.accuracyMeters)
        assertEquals(point.batteryPercent, stored.batteryPercent)
        assertEquals(SyncState.PENDING, stored.syncState)
    }

    @Test
    fun conserve_les_champs_optionnels_absents() = runTest {
        val bare = pointAt(0).copy(accuracyMeters = null, altitudeMeters = null, batteryPercent = null)
        store.save(bare)

        val stored = store.oldestPending(limit = 10).single()

        assertNull(stored.accuracyMeters)
        assertNull(stored.altitudeMeters)
        assertNull(stored.batteryPercent)
    }

    @Test
    fun rend_les_points_en_attente_du_plus_ancien_au_plus_recent() = runTest {
        // Insérés dans le désordre : c'est la requête qui doit ordonner,
        // pas l'ordre d'insertion.
        listOf(30L, 0L, 10L).forEach { store.save(pointAt(it)) }

        val pending = store.oldestPending(limit = 10)

        assertEquals(
            listOf(0L, 10L, 30L).map { baseTime.plusSeconds(it * 60) },
            pending.map { it.recordedAt },
        )
    }

    @Test
    fun respecte_la_taille_de_lot_demandee() = runTest {
        (0L until 10L).forEach { store.save(pointAt(it)) }

        assertEquals(3, store.oldestPending(limit = 3).size)
    }

    @Test
    fun marque_synchronise_uniquement_les_identifiants_confirmes() = runTest {
        val confirmed = pointAt(0)
        val untouched = pointAt(10)
        store.save(confirmed)
        store.save(untouched)

        store.markSynced(listOf(confirmed.id), at = baseTime.plusSeconds(60))

        val stillPending = store.oldestPending(limit = 10)
        assertEquals(listOf(untouched.id), stillPending.map { it.id })
        assertEquals(1, store.pendingCount())
    }

    @Test
    fun un_point_deja_synchronise_ne_repart_pas_en_attente_si_on_le_reinsere() = runTest {
        // Scénario réel : le serveur a confirmé le point, puis une capture
        // rejouée ou une réinsertion tente de l'écrire à nouveau.
        val point = pointAt(0)
        store.save(point)
        store.markSynced(listOf(point.id), at = baseTime.plusSeconds(60))

        store.save(point)

        assertEquals(0, store.pendingCount())
        assertTrue(store.oldestPending(limit = 10).isEmpty())
    }

    @Test
    fun un_echec_incremente_le_compteur_sans_changer_l_etat_de_synchronisation() = runTest {
        val point = pointAt(0)
        store.save(point)

        store.recordFailedAttempt(listOf(point.id), at = baseTime.plusSeconds(60), errorCode = "timeout")
        store.recordFailedAttempt(listOf(point.id), at = baseTime.plusSeconds(120), errorCode = "server_error_503")

        val stored = store.oldestPending(limit = 10).single()
        assertEquals(SyncState.PENDING, stored.syncState)
        assertEquals(2, stored.attemptCount)
        assertEquals("server_error_503", stored.lastErrorCode)
        assertEquals(baseTime.plusSeconds(120), stored.lastAttemptAt)
    }

    @Test
    fun un_echec_ne_touche_pas_un_point_deja_confirme() = runTest {
        val point = pointAt(0)
        store.save(point)
        store.markSynced(listOf(point.id), at = baseTime.plusSeconds(60))

        store.recordFailedAttempt(listOf(point.id), at = baseTime.plusSeconds(120), errorCode = "timeout")

        assertEquals(0, store.pendingCount())
    }

    @Test
    fun marquer_synchronise_efface_la_derniere_erreur() = runTest {
        val point = pointAt(0)
        store.save(point)
        store.recordFailedAttempt(listOf(point.id), at = baseTime.plusSeconds(60), errorCode = "timeout")

        store.markSynced(listOf(point.id), at = baseTime.plusSeconds(120))

        assertEquals(0, store.pendingCount())
    }

    @Test
    fun signale_l_age_du_plus_vieux_point_en_attente() = runTest {
        val oldest = pointAt(0)
        store.save(oldest)
        store.save(pointAt(30))

        assertEquals(oldest.recordedAt, store.oldestPendingRecordedAt())

        store.markSynced(listOf(oldest.id), at = baseTime)

        assertEquals(baseTime.plusSeconds(30 * 60), store.oldestPendingRecordedAt())
    }

    @Test
    fun la_derniere_position_connue_ignore_l_etat_de_synchronisation() = runTest {
        val recent = pointAt(30)
        store.save(pointAt(0))
        store.save(recent)
        store.markSynced(listOf(recent.id), at = baseTime)

        assertEquals(recent.recordedAt, store.lastRecordedAt())
    }

    @Test
    fun une_base_vide_ne_rend_aucune_date() = runTest {
        assertNull(store.lastRecordedAt())
        assertNull(store.oldestPendingRecordedAt())
        assertEquals(0, store.pendingCount())
    }

    @Test
    fun un_etat_de_synchronisation_inconnu_reste_dans_le_backlog() = runTest {
        database.locationDao().insert(
            LocationEntity(
                id = "point-ancien-schema",
                latitude = 48.85,
                longitude = 2.29,
                recordedAtEpochMillis = baseTime.toEpochMilli(),
                accuracyMeters = 12f,
                altitudeMeters = null,
                speedMetersPerSecond = null,
                batteryPercent = 62,
                syncState = "ARCHIVED_BY_FUTURE_VERSION",
                attemptCount = 0,
                lastAttemptAtEpochMillis = null,
                lastErrorCode = null,
            ),
        )

        val pending = store.oldestPending(limit = 10).single()

        // Le fallback de conversion ne sert a rien si la requete SQL rend le
        // point invisible : une valeur inconnue doit rester a envoyer.
        assertEquals(LocationId("point-ancien-schema"), pending.id)
        assertEquals(SyncState.PENDING, pending.syncState)
        assertEquals(1, store.pendingCount())
        assertEquals(baseTime, store.oldestPendingRecordedAt())
    }

    @Test
    fun un_etat_inconnu_peut_etre_confirme_par_le_serveur() = runTest {
        database.locationDao().insert(
            LocationEntity(
                id = "point-a-confirmer",
                latitude = 48.85,
                longitude = 2.29,
                recordedAtEpochMillis = baseTime.toEpochMilli(),
                accuracyMeters = null,
                altitudeMeters = null,
                speedMetersPerSecond = null,
                batteryPercent = null,
                syncState = "VALEUR_INATTENDUE",
                attemptCount = 0,
                lastAttemptAtEpochMillis = null,
                lastErrorCode = null,
            ),
        )

        store.markSynced(listOf(LocationId("point-a-confirmer")), at = baseTime.plusSeconds(60))

        assertEquals(0, store.pendingCount())
        assertTrue(store.oldestPending(limit = 10).isEmpty())
    }

    @Test
    fun un_etat_inconnu_garde_la_trace_d_un_echec_d_envoi() = runTest {
        database.locationDao().insert(
            LocationEntity(
                id = "point-en-erreur",
                latitude = 48.85,
                longitude = 2.29,
                recordedAtEpochMillis = baseTime.toEpochMilli(),
                accuracyMeters = null,
                altitudeMeters = null,
                speedMetersPerSecond = null,
                batteryPercent = null,
                syncState = "ETAT_INATTENDU",
                attemptCount = 0,
                lastAttemptAtEpochMillis = null,
                lastErrorCode = null,
            ),
        )

        store.recordFailedAttempt(
            listOf(LocationId("point-en-erreur")),
            at = baseTime.plusSeconds(60),
            errorCode = "timeout",
        )

        val stored = store.oldestPending(limit = 10).single()
        assertEquals(SyncState.PENDING, stored.syncState)
        assertEquals(1, stored.attemptCount)
        assertEquals("timeout", stored.lastErrorCode)
    }

    @Test
    fun confirme_un_backlog_plus_grand_que_la_limite_de_parametres_sqlite() = runTest {
        // Une semaine hors réseau à 5 minutes produit environ 2 000 points.
        // La confirmation doit passer malgré la limite de paramètres SQLite.
        val points = (0L until 1_200L).map { pointAt(it) }
        points.forEach { store.save(it) }
        assertEquals(1_200, store.pendingCount())

        store.markSynced(points.map { it.id }, at = baseTime)

        assertEquals(0, store.pendingCount())
    }
}
