package com.madhi.tracker.domain

/** Une échelle graphique : une distance ronde et la largeur qu'elle occupe. */
data class ScaleBar(val distanceMeters: Int, val widthPixels: Double)

/**
 * L'échelle dessinée sous la carte.
 *
 * Sans fond de carte, c'est la seule chose qui dise si le tracé visible fait
 * deux rues ou deux cents kilomètres. Elle n'est donc pas décorative : elle
 * remplace l'information que les tuiles apporteraient.
 */
object MapScaleBar {

    /**
     * Distances « rondes » : celles qu'on lit sans les traduire. Un pas de
     * 3 km ou de 750 m obligerait à réfléchir.
     */
    private val STEPS_METERS = listOf(
        10, 20, 50,
        100, 200, 500,
        1_000, 2_000, 5_000,
        10_000, 20_000, 50_000,
        100_000, 200_000, 500_000,
        1_000_000, 2_000_000,
    )

    /**
     * La plus grande distance ronde qui tient dans [maxWidthPixels].
     *
     * Renvoie `null` quand même le plus petit pas déborde — carte minuscule
     * ou zoom extrême — auquel cas mieux vaut ne rien dessiner qu'une échelle
     * fausse.
     */
    fun fit(metersPerPixel: Double, maxWidthPixels: Double): ScaleBar? {
        if (metersPerPixel <= 0.0 || maxWidthPixels <= 0.0) return null
        val maxMeters = metersPerPixel * maxWidthPixels
        val distance = STEPS_METERS.lastOrNull { it <= maxMeters } ?: return null
        return ScaleBar(distance, distance / metersPerPixel)
    }
}
