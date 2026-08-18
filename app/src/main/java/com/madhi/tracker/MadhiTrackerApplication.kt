package com.madhi.tracker

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Racine de composition. Hilt assemble ici les ports et leurs adaptateurs ;
 * ni le domaine ni les use cases ne connaissent Hilt.
 */
@HiltAndroidApp
class MadhiTrackerApplication : Application()
