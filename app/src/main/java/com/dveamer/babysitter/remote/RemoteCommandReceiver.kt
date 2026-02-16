package com.dveamer.babysitter.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dveamer.babysitter.BabySitterApplication
import com.dveamer.babysitter.settings.SettingsPatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RemoteCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as BabySitterApplication
                val handler = app.container.remoteCommandHandler

                when (intent.action) {
                    ACTION_REMOTE_SLEEP -> {
                        val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
                        handler.onRemoteSleep(enabled)
                    }

                    ACTION_REMOTE_SETTINGS -> {
                        val patch = SettingsPatch(
                            cryThresholdSec = intent.getIntExtra(EXTRA_CRY_SEC, -1).takeIf { it > 0 },
                            movementThresholdSec = intent.getIntExtra(EXTRA_MOVE_SEC, -1).takeIf { it > 0 },
                            cameraMonitoringEnabled = if (intent.hasExtra(EXTRA_CAMERA_ENABLED)) {
                                intent.getBooleanExtra(EXTRA_CAMERA_ENABLED, false)
                            } else {
                                null
                            }
                        )
                        handler.onRemoteSettings(patch)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMOTE_SLEEP = "com.dveamer.babysitter.remote.SLEEP"
        const val ACTION_REMOTE_SETTINGS = "com.dveamer.babysitter.remote.SETTINGS"

        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_CRY_SEC = "cry_sec"
        const val EXTRA_MOVE_SEC = "move_sec"
        const val EXTRA_CAMERA_ENABLED = "camera_enabled"
    }
}
