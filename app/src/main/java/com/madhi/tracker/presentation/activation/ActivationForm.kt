package com.madhi.tracker.presentation.activation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.madhi.tracker.domain.error.ActivationFailure
import com.madhi.tracker.presentation.common.TrackingStatusColors

/**
 * Le formulaire d'activation, sans état propre.
 *
 * Partagé entre l'onboarding et les réglages : la voyageuse peut avoir à
 * réactiver l'appareil en cours de voyage — token révoqué, changement de
 * téléphone — et il serait absurde de lui faire refaire les six écrans de
 * configuration pour saisir un code.
 */
@Composable
fun ActivationForm(
    code: String,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    busy: Boolean,
    error: ActivationFailure?,
    submitLabel: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text("Code d'activation") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let { Text(activationErrorMessage(it), color = TrackingStatusColors.broken) }

        if (busy) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = onSubmit,
                enabled = code.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(submitLabel) }
        }
    }
}

/**
 * Un message par cause, parce que les gestes à faire diffèrent : redemander
 * un code, attendre, ou simplement retrouver du réseau.
 */
fun activationErrorMessage(failure: ActivationFailure): String = when (failure) {
    ActivationFailure.InvalidCode -> "Ce code n'est pas valide. Vérifie la saisie."
    ActivationFailure.ExpiredCode -> "Ce code a expiré ou a déjà été utilisé. Demande-en un nouveau."
    ActivationFailure.NoNetwork -> "Pas de réseau. Réessaie une fois connectée."
    is ActivationFailure.RateLimited -> "Trop de tentatives. Attends quelques minutes."
    is ActivationFailure.ServerError -> "Le serveur ne répond pas correctement. Réessaie plus tard."
    is ActivationFailure.Unexpected -> "Erreur inattendue. Réessaie."
}
