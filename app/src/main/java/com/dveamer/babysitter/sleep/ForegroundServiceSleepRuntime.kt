package com.dveamer.babysitter.sleep

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ForegroundServiceSleepRuntime(
    private val context: Context
) : SleepRuntime {

    override suspend fun start() {
        val intent = Intent(context, SleepForegroundService::class.java)
            .setAction(SleepForegroundService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    override suspend fun stop() {
        val intent = Intent(context, SleepForegroundService::class.java)
            .setAction(SleepForegroundService.ACTION_STOP)
        context.startService(intent)
    }
}
