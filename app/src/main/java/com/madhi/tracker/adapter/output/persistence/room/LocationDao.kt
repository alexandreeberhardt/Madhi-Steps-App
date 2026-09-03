package com.madhi.tracker.adapter.output.persistence.room

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Ce DAO ne contient volontairement aucune requête de suppression.
 *
 * La V1 ne supprime rien (ADR-005). L'absence de la méthode garantit mieux
 * cette règle qu'un commentaire : on ne peut pas appeler ce qui n'existe pas.
 */
@Dao
interface LocationDao {

    /**
     * IGNORE et non REPLACE : réinsérer un identifiant existant ne doit
     * jamais écraser son état de synchronisation. Un point déjà confirmé par
     * le serveur repartirait sinon en PENDING.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: LocationEntity)

    @Query(
        """
        SELECT * FROM locations
        WHERE sync_state != 'SYNCED'
        ORDER BY recorded_at ASC
        LIMIT :limit
        """,
    )
    suspend fun oldestPending(limit: Int): List<LocationEntity>

    /**
     * La clause `sync_state != 'SYNCED'` est un garde-fou, pas une
     * optimisation : elle empêche de réécrire un point déjà confirmé si deux
     * synchronisations se recouvraient, tout en gardant les valeurs inconnues
     * dans le backlog.
     */
    @Query(
        """
        UPDATE locations
        SET sync_state = 'SYNCED', last_attempt_at = :atEpochMillis, last_error_code = NULL
        WHERE id IN (:ids) AND sync_state != 'SYNCED'
        """,
    )
    suspend fun markSynced(ids: List<String>, atEpochMillis: Long): Int

    @Query(
        """
        UPDATE locations
        SET attempt_count = attempt_count + 1,
            last_attempt_at = :atEpochMillis,
            last_error_code = :errorCode
        WHERE id IN (:ids) AND sync_state != 'SYNCED'
        """,
    )
    suspend fun recordFailedAttempt(ids: List<String>, atEpochMillis: Long, errorCode: String): Int

    @Query("SELECT COUNT(*) FROM locations WHERE sync_state != 'SYNCED'")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM locations WHERE recorded_at >= :sinceEpochMillis")
    suspend fun countRecordedSince(sinceEpochMillis: Long): Int

    @Query("SELECT COUNT(*) FROM locations WHERE sync_state != 'SYNCED'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT MAX(recorded_at) FROM locations")
    suspend fun lastRecordedAt(): Long?

    @Query("SELECT MAX(recorded_at) FROM locations")
    fun observeLastRecordedAt(): Flow<Long?>

    @Query("SELECT MIN(recorded_at) FROM locations WHERE sync_state != 'SYNCED'")
    suspend fun oldestPendingRecordedAt(): Long?

    /**
     * Le tracé de la carte, colonnes utiles seulement et un point par pas de
     * temps.
     *
     * `SELECT *` chargerait douze colonnes pour en dessiner trois, sur une
     * table qui compte plus de cent mille lignes au bout d'un an. Le
     * regroupement par pas de temps borne le résultat sans plafond arbitraire :
     * « tout le voyage » rend une position par heure, pas les cent mille.
     *
     * `MIN(recorded_at)` choisit la position la plus ancienne de chaque pas,
     * et SQLite prend latitude, longitude et état sur **cette ligne-là** —
     * comportement documenté des colonnes nues accompagnant un MIN. La
     * qualification `locations.recorded_at` dans le GROUP BY est nécessaire :
     * sans elle, le nom désignerait l'alias de sortie, qui est un agrégat.
     */
    @Query(
        """
        SELECT latitude, longitude, MIN(locations.recorded_at) AS recorded_at, sync_state
        FROM locations
        WHERE locations.recorded_at >= :sinceEpochMillis
          AND (accuracy_m IS NULL OR accuracy_m <= 250)
        GROUP BY locations.recorded_at / :bucketMillis
        ORDER BY MIN(locations.recorded_at) ASC
        """,
    )
    fun observeTrack(sinceEpochMillis: Long, bucketMillis: Long): Flow<List<TrackPointRow>>
}

/** Projection de lecture : trois valeurs à dessiner, pas une ligne entière. */
data class TrackPointRow(
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "recorded_at") val recordedAtEpochMillis: Long,
    @ColumnInfo(name = "sync_state") val syncState: String,
)
