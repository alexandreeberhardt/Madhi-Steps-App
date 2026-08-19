package com.madhi.tracker.domain.model

/**
 * Liste fermée des événements dignes d'un log, dérivée de ce qu'il faudra
 * réellement diagnostiquer à distance pendant le voyage.
 *
 * Une liste fermée plutôt qu'une chaîne libre : elle empêche de noyer
 * Logcat, et elle rend le filtrage prévisible quand la voyageuse enverra un
 * export depuis la Norvège.
 */
enum class TrackerEvent {
    TRACKING_STARTED,
    TRACKING_STOPPED,
    CAPTURE_INTERVAL_CHANGED,

    /**
     * Délai réellement demandé pour la prochaine acquisition. Une cadence
     * qui dérive est invisible autrement : on ne voit que ses effets, des
     * mois plus tard, sur la batterie.
     */
    CAPTURE_SCHEDULED,

    /** Abonnement au flux de positions ouvert, avec les fournisseurs retenus. */
    STREAM_STARTED,
    STREAM_STOPPED,

    /** Le flux n'a rien livré depuis trop longtemps : le filet prend le relais. */
    STREAM_SILENT,

    LOCATION_ACQUIRED,
    LOCATION_SAVED,
    LOCATION_REJECTED,
    ACQUISITION_FAILED,

    SYNC_STARTED,
    SYNC_SUCCESS,
    SYNC_FAILED,

    DEVICE_ACTIVATED,
    TRACKING_RECOVERED_AFTER_BOOT,
    AUTOSTART_BLOCKED_DETECTED,
    TRACKING_SERVICE_REVIVED,
}
