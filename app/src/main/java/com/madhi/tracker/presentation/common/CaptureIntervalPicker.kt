package com.madhi.tracker.presentation.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.madhi.tracker.domain.model.CaptureInterval

/**
 * Le choix de la cadence : trois paliers, et « Autre » pour le reste.
 *
 * Partagé entre les réglages et le diagnostic, qui offraient deux variantes du
 * même choix. Une seule est plus sûre : c'est le réglage qui décide de la
 * consommation de l'appareil pendant un an.
 */
@Composable
fun CaptureIntervalChips(
    selected: CaptureInterval,
    onSelect: (CaptureInterval) -> Unit,
    modifier: Modifier = Modifier,
) {
    var askingCustom by rememberSaveable { mutableStateOf(false) }

    Row(
        // Défilante comme la rangée des périodes : quatre puces ne tiennent pas
        // sur un écran de 360 points dès que la police du système grandit.
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CaptureInterval.PRESETS.forEach { interval ->
            FilterChip(
                selected = selected == interval,
                onClick = { onSelect(interval) },
                label = { Text(captureIntervalLabel(interval), maxLines = 1) },
            )
        }
        FilterChip(
            selected = !selected.isPreset,
            onClick = { askingCustom = true },
            label = {
                // La puce porte la valeur choisie : sinon « Autre » sélectionné
                // ne dit pas à quelle cadence l'appareil tourne réellement.
                val label =
                    if (selected.isPreset) "Autre" else "Autre : ${captureIntervalLabel(selected)}"
                Text(label, maxLines = 1)
            },
        )
    }

    if (askingCustom) {
        CaptureIntervalDialog(
            initial = selected,
            onDismiss = { askingCustom = false },
            onConfirm = {
                onSelect(it)
                askingCustom = false
            },
        )
    }
}

/**
 * La saisie d'une cadence hors palier.
 *
 * Le bouton de validation reste éteint tant que la valeur est hors bornes :
 * refuser à la saisie vaut mieux que corriger en silence, parce que la
 * personne verrait sinon un chiffre et l'appareil en appliquerait un autre.
 */
@Composable
private fun CaptureIntervalDialog(
    initial: CaptureInterval,
    onDismiss: () -> Unit,
    onConfirm: (CaptureInterval) -> Unit,
) {
    var saisie by rememberSaveable { mutableStateOf(initial.minutes.toString()) }
    val choisi = saisie.toIntOrNull()?.let(CaptureInterval::fromMinutes)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Autre fréquence") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = saisie,
                    // Filtré à la source : le clavier numérique d'Android laisse
                    // passer le signe moins et le séparateur décimal.
                    onValueChange = { saisie = it.filter(Char::isDigit).take(4) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    isError = saisie.isNotEmpty() && choisi == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    if (choisi != null) {
                        "Une position toutes les ${captureIntervalLabel(choisi)}."
                    } else {
                        "Entre ${CaptureInterval.MIN_MINUTES} et " +
                            "${CaptureInterval.MAX_MINUTES} minutes, soit 24 h au plus."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { choisi?.let(onConfirm) },
                enabled = choisi != null,
            ) { Text("Valider") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/**
 * « 5 min », « 1 h », « 1 h 30 ».
 *
 * Les minutes seules deviennent illisibles dès qu'on dépasse l'heure : « 90
 * min » demande un calcul, « 1 h 30 » non.
 */
fun captureIntervalLabel(interval: CaptureInterval): String {
    val minutes = interval.minutes
    if (minutes < MINUTES_PER_HOUR) return "$minutes min"

    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return if (rest == 0) "$hours h" else "$hours h $rest"
}

private const val MINUTES_PER_HOUR = 60
