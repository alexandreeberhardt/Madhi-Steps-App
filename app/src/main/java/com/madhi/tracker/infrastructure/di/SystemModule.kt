package com.madhi.tracker.infrastructure.di

import android.os.SystemClock
import com.madhi.tracker.application.port.Clock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Module
@InstallIn(SingletonComponent::class)
object SystemModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClockAdapter
}

private object SystemClockAdapter : Clock {
    override fun now(): Instant = Instant.now()

    /**
     * `elapsedRealtime` continue de courir en veille profonde, contrairement
     * à `uptimeMillis`. C'est ce qu'il faut pour détecter un redémarrage :
     * un téléphone endormi huit heures a bien vieilli de huit heures.
     */
    override fun uptime(): Duration = SystemClock.elapsedRealtime().milliseconds
}
