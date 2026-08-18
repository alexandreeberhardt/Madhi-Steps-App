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
}
