package com.gshashank.btcagent

import android.app.Application
import com.gshashank.btcagent.data.repository.CatalogRepository
import com.gshashank.btcagent.di.IoDispatcher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Triggers Hilt's component generation for the whole app.
 *
 * Kicks off the FIRST catalog fetch at startup so feature flags (e.g. login_mock) are loaded
 * before/around first screen composition, rather than waiting one 10-minute poll interval.
 * refresh() is fail-open; the screen reacts to the result via CatalogRepository.isEnabledFlow.
 */
@HiltAndroidApp
class BtcApplication : Application() {

    @Inject
    lateinit var catalogRepository: CatalogRepository

    @Inject
    @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    private val appScope by lazy { CoroutineScope(SupervisorJob() + ioDispatcher) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            catalogRepository.refresh()
        }
    }
}
