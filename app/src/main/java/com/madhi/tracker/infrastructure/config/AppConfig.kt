package com.madhi.tracker.infrastructure.config

import com.madhi.tracker.BuildConfig

/**
 * Point unique où l'URL de l'API entre dans le code. Sa valeur vient de
 * `BuildConfig`, alimentée au build par une variable d'environnement ou
 * `local.properties` — jamais versionnée (`arch/00` §8 règle 9).
 */
object AppConfig {
    val apiBaseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')
    val appVersion: String = BuildConfig.VERSION_NAME
    val updatePageUrl: String = BuildConfig.UPDATE_PAGE_URL.trim()

    /**
     * Gabarit d'URL des tuiles, au format `{z}/{x}/{y}`. Vide par défaut, et
     * la carte reste alors sur fond uni : aucun serveur de tuiles n'est
     * choisi dans le dépôt, parce que ce choix engage une licence et parfois
     * un compte. Voir `arch/18` §8.
     */
    val tileUrlTemplate: String = BuildConfig.TILE_URL_TEMPLATE.trim()

    /** Mention légale imposée par la licence des données affichées. */
    val tileAttribution: String = BuildConfig.TILE_ATTRIBUTION.trim()

    /**
     * Dernier niveau de zoom servi par la source. Au-delà, l'application
     * agrandit ce niveau au lieu de demander des tuiles inexistantes.
     */
    val tileMaxZoom: Int = BuildConfig.TILE_MAX_ZOOM
}
