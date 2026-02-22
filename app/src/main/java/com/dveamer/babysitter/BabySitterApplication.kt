package com.dveamer.babysitter

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BabySitterApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "unhandled app coroutine error", throwable)
    }

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        applicationScope.launch(appExceptionHandler) {
            container.initialize()
            container.sleepRuntimeOrchestrator.start(this)
            container.webServiceOrchestrator.start(this)
        }
    }

    private companion object {
        const val TAG = "BabySitterApp"
    }
}
