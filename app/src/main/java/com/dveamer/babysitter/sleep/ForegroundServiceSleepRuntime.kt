package com.dveamer.babysitter.sleep

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class ForegroundServiceSleepRuntime(
    private val context: Context
) : SleepRuntime {

    override suspend fun start() {
        val intent = Intent(context, SleepForegroundService::class.java)
            .setAction(SleepForegroundService.ACTION_START)
        runCatching {
            ContextCompat.startForegroundService(context, intent)
        }.onFailure {
            Log.w(TAG, "failed to start sleep foreground service", it)
        }
    }

    override suspend fun stop() {
        val intent = Intent(context, SleepForegroundService::class.java)
            .setAction(SleepForegroundService.ACTION_STOP)
        runCatching {
            context.startService(intent)
        }.onFailure {
            Log.w(TAG, "failed to stop sleep foreground service", it)
        }
    }

    private companion object {
        const val TAG = "FgServiceSleepRuntime"
    }
}
