package com.madhi.tracker.domain.model

/**
 * Le constructeur du téléphone, parce qu'il change ce qu'il faut demander à
 * l'utilisatrice.
 *
 * Les permissions Android standard ne suffisent pas : chaque surcouche
 * ajoute ses propres verrous, sous des noms différents, dans des menus
 * différents. Une consigne générique du type « désactivez l'optimisation de
 * batterie » ne mène nulle part sur MIUI (ADR-007 §3.4).
 *
 * Cette énumération vit dans le domaine parce que c'est une règle produit —
 * quoi demander à qui — et non une détection technique. La détection, elle,
 * est dans l'adaptateur.
 */
enum class DeviceVendor {
    /** MIUI / HyperOS : démarrage automatique bloqué par défaut. */
    XIAOMI,

    /** OxygenOS / ColorOS : optimisation poussée, réglages parfois réinitialisés. */
    ONEPLUS_OPPO,

    SAMSUNG,
    HUAWEI,

    /** Pixel et assimilés : les réglages Android standard suffisent. */
    OTHER,
    ;

    /**
     * Ce constructeur impose-t-il des réglages en plus de ceux qu'Android
     * expose ? Si non, l'onboarding n'a pas à afficher d'écran de plus.
     */
    val requiresVendorSpecificSetup: Boolean get() = this != OTHER
}
