package com.madhi.tracker.application.port

import java.time.Instant
import kotlin.time.Duration

/**
 * Le temps est injecté parce qu'il intervient dans des règles testables :
 * validation des horodatages, backoff, détection de redémarrage.
 */
interface Clock {
    fun now(): Instant

    /**
     * Temps écoulé depuis le démarrage du téléphone. Contrairement à [now],
     * il est insensible aux changements d'heure et ne recule jamais au sein
     * d'un même démarrage — c'est ce qui rend la détection de redémarrage fiable.
     */
    fun uptime(): Duration
}
