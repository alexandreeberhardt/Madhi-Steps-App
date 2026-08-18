package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.TrackingProblem

/**
 * Ce qui empêche le suivi de fonctionner, déduit de l'état observable.
 *
 * Extrait ici parce que deux écrans en ont besoin — l'accueil pour afficher
 * « Action nécessaire », le diagnostic pour en dresser la liste — et que
 * deux copies de cette règle finiraient par diverger.
 */
object TrackingProblemDetection {

    fun detect(
        hasForegroundLocationPermission: Boolean,
        hasBackgroundLocationPermission: Boolean,
        hasNotificationPermission: Boolean,
        isLocationEnabled: Boolean,
        isIgnoringBatteryOptimizations: Boolean,
        canScheduleExactAlarms: Boolean,
        autostartBlocked: Boolean,
        deviceActivated: Boolean,
        authenticationFailed: Boolean,
    ): List<TrackingProblem> = buildList {
        if (!deviceActivated) add(TrackingProblem.DEVICE_NOT_ACTIVATED)
        if (!hasForegroundLocationPermission) add(TrackingProblem.LOCATION_PERMISSION_MISSING)
        if (!hasBackgroundLocationPermission) add(TrackingProblem.BACKGROUND_LOCATION_PERMISSION_MISSING)
        if (!isLocationEnabled) add(TrackingProblem.LOCATION_DISABLED)
        if (autostartBlocked) add(TrackingProblem.AUTOSTART_BLOCKED)
        if (!isIgnoringBatteryOptimizations) add(TrackingProblem.BATTERY_OPTIMIZATION_ENABLED)
        if (!canScheduleExactAlarms) add(TrackingProblem.EXACT_ALARM_NOT_PERMITTED)
        if (authenticationFailed) add(TrackingProblem.AUTHENTICATION_FAILED)
        if (!hasNotificationPermission) add(TrackingProblem.NOTIFICATIONS_BLOCKED)
    }
}
