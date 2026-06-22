package com.gshashank.btcagent

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point. Triggers Hilt's component generation for the whole app.
 */
@HiltAndroidApp
class BtcApplication : Application()
