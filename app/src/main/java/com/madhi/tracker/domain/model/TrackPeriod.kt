package com.madhi.tracker.domain.model

/**
 * Les périodes affichables par la carte, et elles seules.
 *
 * Le même vocabulaire que le site familial (`site/features/period.js`), à une
 * différence près : le site s'arrête à trente jours parce que le serveur
 * plafonne une réponse à dix mille points et tronque les plus récents. Sur le
 * téléphone la base est locale, il n'y a pas d'appel à plafonner, et « tout le
 * voyage » est donc possible.
 */
enum class TrackPeriod {
    TODAY,
    SEVEN_DAYS,
    EVERYTHING,
}
