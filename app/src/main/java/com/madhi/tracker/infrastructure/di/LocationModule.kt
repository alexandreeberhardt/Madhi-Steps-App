package com.madhi.tracker.infrastructure.di

import com.madhi.tracker.adapter.output.location.AndroidLocationSource
import com.madhi.tracker.adapter.output.system.AndroidTrackingEnvironment
import com.madhi.tracker.application.port.LocationSource
import com.madhi.tracker.application.port.TrackingEnvironment
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    abstract fun bindLocationSource(source: AndroidLocationSource): LocationSource

    @Binds
    abstract fun bindTrackingEnvironment(environment: AndroidTrackingEnvironment): TrackingEnvironment
}
