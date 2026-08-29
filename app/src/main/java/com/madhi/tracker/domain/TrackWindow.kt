package com.madhi.tracker.domain

import com.madhi.tracker.domain.model.TrackPeriod
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Traduit une période en une fenêtre de lecture : depuis quand, et à quelle
 * finesse.
 *
 * La finesse n'est pas un détail d'affichage. Un an de voyage à cinq minutes
 * fait environ cent mille positions ; les relier toutes à chaque image de
 * glissement mettrait à genoux un appareil à 4 Go, pour un tracé où deux
 * points d'une même minute se superposent au pixel près. Chaque période
 * choisit donc un pas de temps qui borne le nombre de points **quelle que
 * soit la cadence de capture réglée** :
 *
 * - aujourd'hui, une minute au plus : 1 440 points ;
 * - vingt-quatre heures, une minute : 1 440 points ;
 * - sept jours, cinq minutes : environ 2 000 points ;
 * - tout le voyage, une heure : environ 8 800 points par an.
 */
object TrackWindow {

    fun since(period: TrackPeriod, now: Instant, zone: ZoneId): Instant = when (period) {
        // Le jour civil de la voyageuse, pas les vingt-quatre dernières
        // heures : « aujourd'hui » désigne une date, pas une durée.
        TrackPeriod.TODAY -> now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        // Une duree, cette fois : elle ne depend pas de l'heure qu'il est.
        TrackPeriod.LAST_24H -> now.minus(24, ChronoUnit.HOURS)

        TrackPeriod.SEVEN_DAYS -> now.minus(7, ChronoUnit.DAYS)

        // Tout ce que la base contient. Sur l'appareil du voyage, elle
        // commence à l'activation ; sur un appareil de pré-validation, elle
        // contient aussi les positions des tests, prises à la maison.
        TrackPeriod.EVERYTHING -> Instant.EPOCH
    }

    fun bucketMillis(period: TrackPeriod): Long = when (period) {
        TrackPeriod.TODAY -> 60_000L

        // Meme pas qu'« aujourd'hui » : la periode ne peut pas etre plus
        // longue qu'un jour, donc le meme plafond de points la borne.
        TrackPeriod.LAST_24H -> 60_000L

        TrackPeriod.SEVEN_DAYS -> 300_000L
        TrackPeriod.EVERYTHING -> 3_600_000L
    }
}
