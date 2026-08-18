package com.madhi.tracker.infrastructure.di

import com.madhi.tracker.adapter.output.scheduling.AlarmCaptureScheduler
import com.madhi.tracker.adapter.output.system.AndroidTrackingRuntime
import com.madhi.tracker.application.port.CaptureScheduler
import com.madhi.tracker.application.port.TrackingRuntime
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {

    @Binds
    abstract fun bindCaptureScheduler(scheduler: AlarmCaptureScheduler): CaptureScheduler

    @Binds
    abstract fun bindTrackingRuntime(runtime: AndroidTrackingRuntime): TrackingRuntime
}
