package com.madhi.tracker.presentation.onboarding

import com.madhi.tracker.domain.model.DeviceVendor

/**
 * Les instructions constructeur, écran par écran.
 *
 * C'est le contenu le plus important de l'onboarding et le moins
 * technique : trois de ces réglages ne peuvent pas être accordés par
 * l'application, elle ne peut que dire où aller. Un texte générique du type
 * « désactivez l'optimisation de batterie » ne mène nulle part sur MIUI.
 *
 * Chaque étape dit ce qui casse sans elle : une consigne dont on ne
 * comprend pas l'enjeu est une consigne qu'on saute.
 */
data class VendorStep(
    val title: String,
    val path: String,
    val consequence: String,
)

object VendorSetupGuidance {

    fun stepsFor(vendor: DeviceVendor): List<VendorStep> = when (vendor) {
        DeviceVendor.XIAOMI -> listOf(
            VendorStep(
                title = "Démarrage auto en arrière-plan",
                path = "Paramètres › Applications › Madhi Tracker › Autorisations › Démarrage auto",
                consequence = "Sans lui, le suivi ne repart pas après un redémarrage du téléphone.",
            ),
            VendorStep(
                title = "Économiseur de batterie : aucune restriction",
                path = "Paramètres › Applications › Madhi Tracker › Économiseur de batterie › Aucune restriction",
                consequence = "MIUI ajoute sa propre limite, en plus de celle d'Android. Sans ce réglage, le suivi est coupé en veille.",
            ),
            VendorStep(
                title = "Verrouiller dans les applications récentes",
                path = "Applications récentes › tirer la vignette vers le bas › cadenas",
                consequence = "Sans le verrou, balayer l'application l'arrête.",
            ),
        )

        DeviceVendor.ONEPLUS_OPPO -> listOf(
            VendorStep(
                title = "Optimisation avancée désactivée",
                path = "Paramètres › Batterie › Optimisation de la batterie › ⋮ › Optimisation avancée",
                consequence = "Désactiver « Optimisation poussée » et « Optimisation de la veille ». C'est le principal tueur d'applications.",
            ),
            VendorStep(
                title = "Lancement automatique",
                path = "Paramètres › Applications › Madhi Tracker › lancement automatique",
                consequence = "Sans lui, le suivi ne repart pas après un redémarrage.",
            ),
            VendorStep(
                title = "Verrouiller dans les applications récentes",
                path = "Applications récentes › appui long sur la vignette › cadenas",
                consequence = "Le système réinitialise parfois seul l'exemption de batterie ; le verrou l'en empêche.",
            ),
        )

        DeviceVendor.SAMSUNG -> listOf(
            VendorStep(
                title = "Retirer des applications en veille",
                path = "Paramètres › Batterie › Limites d'utilisation en arrière-plan",
                consequence = "Vérifier que Madhi Tracker n'est ni en veille, ni en veille profonde.",
            ),
            VendorStep(
                title = "Désactiver l'optimisation adaptative",
                path = "Paramètres › Batterie › Plus de paramètres › Batterie adaptative",
                consequence = "L'apprentissage d'usage finit par restreindre une application ouverte rarement.",
            ),
        )

        DeviceVendor.HUAWEI -> listOf(
            VendorStep(
                title = "Lancement manuel autorisé",
                path = "Paramètres › Applications › Madhi Tracker › Lancement › gérer manuellement",
                consequence = "Activer les trois options : lancement auto, lancement secondaire, exécution en arrière-plan.",
            ),
        )

        DeviceVendor.OTHER -> emptyList()
    }
}
