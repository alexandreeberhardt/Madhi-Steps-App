package com.madhi.tracker.domain.model

import java.time.Instant

/**
 * Ce que le fournisseur de localisation rend : une mesure brute, sans
 * identité ni état de synchronisation. Elle devient un [LocationPoint]
 * seulement après validation.
 */
data class LocationFix(
    val coordinates: Coordinates,
    val recordedAt: Instant,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Float?,
)
