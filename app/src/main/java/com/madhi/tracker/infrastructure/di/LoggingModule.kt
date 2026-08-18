package com.madhi.tracker.infrastructure.di

import com.madhi.tracker.application.port.EventLog
import com.madhi.tracker.infrastructure.log.AndroidEventLog
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingModule {

    @Binds
    abstract fun bindEventLog(eventLog: AndroidEventLog): EventLog
}
