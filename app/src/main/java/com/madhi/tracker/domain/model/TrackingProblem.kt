package com.madhi.tracker.domain.model

/**
 * Ce qui empêche le suivi de fonctionner, du plus grave au plus bénin.
 *
 * L'ordre de déclaration est l'ordre de résolution : l'écran principal
 * n'affiche que le premier problème, parce qu'en corriger un peut suffire
 * et qu'une liste de sept avertissements ne serait pas actionnable.
 */
enum class TrackingProblem(
    /**
     * Ce problème finira-t-il par faire perdre des positions ?
     *
     * Seuls ceux-là déclenchent l'état rouge « Action nécessaire ». Une
     * notification masquée gêne le diagnostic mais n'interrompt pas le suivi :
     * l'afficher en rouge en permanence apprendrait à ignorer le rouge.
     */
    val causesDataLoss: Boolean,
) {

    /** Sans activation, aucun point ne partira jamais. */
    DEVICE_NOT_ACTIVATED(causesDataLoss = true),

    LOCATION_PERMISSION_MISSING(causesDataLoss = true),

    /** Sans elle, pas de reprise après redémarrage du téléphone. */
    BACKGROUND_LOCATION_PERMISSION_MISSING(causesDataLoss = true),

    LOCATION_DISABLED(causesDataLoss = true),

    /**
     * Détecté après coup : le téléphone a redémarré et notre code ne s'est
     * pas réveillé. Sur MIUI, c'est le démarrage automatique qui est bloqué
     * (ADR-007 §3.1).
     */
    AUTOSTART_BLOCKED(causesDataLoss = true),

    /**
     * Sans exemption, la cadence dérive en veille et le watchdog ne peut
     * plus ressusciter le service.
     */
    BATTERY_OPTIMIZATION_ENABLED(causesDataLoss = true),

    EXACT_ALARM_NOT_PERMITTED(causesDataLoss = true),

    /** Le token a été refusé : les points s'accumulent sans jamais partir. */
    AUTHENTICATION_FAILED(causesDataLoss = true),

    /** La notification du service est masquée : l'état du suivi devient invisible. */
    NOTIFICATIONS_BLOCKED(causesDataLoss = false),
    ;
}
