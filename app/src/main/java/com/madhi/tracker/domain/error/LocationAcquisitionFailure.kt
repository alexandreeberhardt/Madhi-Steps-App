package com.madhi.tracker.domain.error

sealed interface LocationAcquisitionFailure {

    val code: String

    /** La permission de localisation manque : rien ne servira de réessayer. */
    data object PermissionMissing : LocationAcquisitionFailure {
        override val code = "permission_missing"
    }

    /** La localisation est désactivée dans les réglages système. */
    data object LocationDisabled : LocationAcquisitionFailure {
        override val code = "location_disabled"
    }

    /** Aucun point fixé dans le délai imparti. Cas normal en ville dense ou en tunnel. */
    data object Timeout : LocationAcquisitionFailure {
        override val code = "timeout"
    }

    /** Le fournisseur a rendu une mesure que la validation a refusée. */
    data class Invalid(val reason: String) : LocationAcquisitionFailure {
        override val code = "invalid_$reason"
    }
}
