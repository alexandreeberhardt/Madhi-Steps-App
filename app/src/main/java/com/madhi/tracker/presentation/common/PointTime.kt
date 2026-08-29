package com.madhi.tracker.presentation.common

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * L'heure d'une position, en toutes lettres.
 *
 * C'est l'exception à la règle d'`arch/09` §2, qui proscrit l'horodatage sur
 * l'écran d'accueil : celle-là parle du suivi, qui doit se juger à son
 * ancienneté, pas à une heure exacte qui laisserait croire au temps réel.
 * Ici, la question est autre — « on était où à ce moment-là ? » —, et elle
 * appelle une date.
 *
 * Le fuseau est celui du téléphone, donc celui du pays traversé. C'est le bon :
 * ce qui compte est l'heure qu'il faisait sur place.
 */
fun pointTimeLabel(instant: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val moment = instant.atZone(zone)
    val heure = HOUR_FORMAT.format(moment)

    val jour = moment.toLocalDate()
    val aujourdHui = now.atZone(zone).toLocalDate()
    return when (jour) {
        aujourdHui -> "aujourd'hui à $heure"
        aujourdHui.minusDays(1) -> "hier à $heure"
        // L'année n'apparaît que si elle diffère : un voyage d'un an finira
        // par en traverser deux, et « 3 janvier » serait alors ambigu.
        else -> {
            val date = DAY_FORMAT.format(moment)
            val annee = if (jour.year == aujourdHui.year) "" else " ${jour.year}"
            "le $date$annee à $heure"
        }
    }
}

private val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)
