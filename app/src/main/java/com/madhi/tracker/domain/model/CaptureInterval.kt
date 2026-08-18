package com.madhi.tracker.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Liste courte et fermée, pas de saisie libre : `arch/01` §2 et
 * `arch/09` §5. Une valeur libre exposerait la batterie à une erreur de
 * saisie qu'on ne pourrait pas corriger à distance pendant le voyage.
 */
enum class CaptureInterval(val minutes: Int) {
    TWO(2),
    FIVE(5),
    TEN(10),
    FIFTEEN(15),
    THIRTY(30),
    ;

    val duration: Duration get() = minutes.minutes

    /**
     * Deux minutes maintient le GPS quasi continuellement allumé. L'écran de
     * réglages doit le dire (`arch/02` §2).
     */
    val hasSignificantBatteryCost: Boolean get() = this == TWO

    companion object {
        val DEFAULT = FIVE

        fun fromMinutes(minutes: Int): CaptureInterval? = entries.find { it.minutes == minutes }
    }
}
