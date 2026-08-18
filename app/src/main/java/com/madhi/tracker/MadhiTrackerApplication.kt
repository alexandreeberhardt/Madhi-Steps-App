package com.madhi.tracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Racine de composition. Hilt assemble ici les ports et leurs adaptateurs ;
 * ni le domaine ni les use cases ne connaissent Hilt.
 */
@HiltAndroidApp
class MadhiTrackerApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Initialisation à la demande de WorkManager : sans elle, la fabrique
     * Hilt ne serait pas branchée et SyncWorker ne pourrait pas recevoir ses
     * dépendances.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
