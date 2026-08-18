package com.madhi.tracker.application.port

import kotlinx.coroutines.flow.Flow

/**
 * Le parcours de première configuration n'est franchi qu'une fois, mais son
 * état doit survivre à une mise à jour d'APK : la voyageuse ne doit pas
 * revoir cinq écrans de permissions à chaque nouvelle version installée à la
 * main pendant le voyage.
 */
interface OnboardingStore {
    suspend fun isCompleted(): Boolean
    suspend fun markCompleted()
    fun observe(): Flow<Boolean>
}
