package com.madhi.tracker.application.port

import com.madhi.tracker.domain.model.DeviceVendor
import kotlinx.coroutines.flow.Flow

/**
 * L'état du système tel que l'application peut le constater : permissions,
 * localisation activée, exemption batterie, réseau.
 *
 * Volontairement en lecture seule. Trois des réglages qui comptent sur MIUI
 * ne peuvent pas être accordés par l'application (ADR-007) : elle ne peut
 * que constater et guider.
 */
interface TrackingEnvironment {

    fun snapshot(): EnvironmentSnapshot

    fun observe(): Flow<EnvironmentSnapshot>
}

data class EnvironmentSnapshot(
    val hasForegroundLocationPermission: Boolean,
    val hasBackgroundLocationPermission: Boolean,
    val hasNotificationPermission: Boolean,
    val isLocationEnabled: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
    val canScheduleExactAlarms: Boolean,
    val isOnline: Boolean,
    val batteryPercent: Int?,
    val vendor: DeviceVendor,
)
