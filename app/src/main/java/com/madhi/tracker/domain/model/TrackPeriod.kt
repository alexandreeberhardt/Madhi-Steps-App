package com.madhi.tracker.domain.model

/**
 * Les périodes affichables par la carte, et elles seules.
 *
 * Le même vocabulaire que le site familial (`site/features/period.js`), à une
 * différence près : le site propose en plus trente jours, qui n'aurait pas de
 * sens ici. Les deux savent montrer tout le voyage — le site depuis que le
 * serveur échantillonne au lieu de tronquer, le téléphone depuis toujours,
 * parce que sa base est locale et qu'il n'y a aucun appel à borner.
 */
enum class TrackPeriod {
    TODAY,

    /**
     * Les vingt-quatre dernieres heures, et non la journee civile.
     *
     * Les deux se ressemblent a midi et n'ont plus rien a voir a une heure du
     * matin, ou « aujourd'hui » ne montre qu'une heure de trajet. C'est la
     * periode qui repond a « ou en est-elle depuis hier ? » sans dependre de
     * l'heure a laquelle on regarde.
     */
    LAST_24H,

    SEVEN_DAYS,
    EVERYTHING,
}
