package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.LocationFix
import java.time.Instant
import kotlin.time.Duration.Companion.days

/**
 * Refuse les mesures manifestement fausses, et rien d'autre.
 *
 * La validation est délibérément permissive. Perdre une position est le
 * deuxième risque du projet après la panne de suivi : une mesure imprécise
 * reste une information utile à la famille, alors qu'un point refusé est
 * perdu définitivement. On n'écarte donc que ce qui ne peut pas être vrai.
 */
object LocationValidation {

    /**
     * Une horloge revenue à l'époque Unix, ou un rollover de semaine GPS,
     * produit des dates absurdes. `arch/02` §6 demande explicitement une
     * protection contre les timestamps incohérents.
     */
    private val EARLIEST_PLAUSIBLE_DATE: Instant = Instant.parse("2026-01-01T00:00:00Z")

    private val MAX_CLOCK_DRIFT_AHEAD = 1.days

    fun validate(fix: LocationFix, now: Instant): Outcome<LocationFix, Rejection> = when {
        fix.coordinates.latitude !in -90.0..90.0 ->
            failure(Rejection.LatitudeOutOfRange)

        fix.coordinates.longitude !in -180.0..180.0 ->
            failure(Rejection.LongitudeOutOfRange)

        fix.coordinates.latitude.isNaN() || fix.coordinates.longitude.isNaN() ->
            failure(Rejection.NotANumber)

        // (0, 0) est en plein golfe de Guinée. Sur un trajet France-Norvège,
        // c'est la signature d'un fix raté, jamais une position réelle.
        isNullIsland(fix) ->
            failure(Rejection.NullIsland)

        fix.recordedAt.isBefore(EARLIEST_PLAUSIBLE_DATE) ->
            failure(Rejection.TimestampTooOld)

        fix.recordedAt.isAfter(now.plusMillis(MAX_CLOCK_DRIFT_AHEAD.inWholeMilliseconds)) ->
            failure(Rejection.TimestampInFuture)

        else -> success(fix)
    }

    private fun isNullIsland(fix: LocationFix): Boolean =
        fix.coordinates.latitude == 0.0 && fix.coordinates.longitude == 0.0

    enum class Rejection(val code: String) {
        LatitudeOutOfRange("latitude_out_of_range"),
        LongitudeOutOfRange("longitude_out_of_range"),
        NotANumber("not_a_number"),
        NullIsland("null_island"),
        TimestampTooOld("timestamp_too_old"),
        TimestampInFuture("timestamp_in_future"),
    }
}
