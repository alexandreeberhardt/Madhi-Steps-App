package com.madhi.tracker.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Point d'entrée de la navigation. Reste vide tant que les écrans réels
 * n'existent pas : la phase de bootstrap ne livre aucune interface.
 */
@Composable
fun MadhiTrackerApp() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "Madhi Tracker")
        }
    }
}
