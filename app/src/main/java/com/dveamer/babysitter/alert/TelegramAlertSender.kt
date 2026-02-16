package com.dveamer.babysitter.alert

import android.content.Context
import android.util.Log
import com.dveamer.babysitter.settings.SettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class TelegramAlertSender(
    _context: Context
) : AlertSender {

    override suspend fun sendWakeAlert(settings: SettingsState, awakeDurationSec: Long) {
        val token = settings.telegramBotToken
        val chatId = settings.telegramChatId
        if (token.isBlank() || chatId.isBlank()) return

        withContext(Dispatchers.IO) {
            val text = "Baby is awake for ${awakeDurationSec}s"
            val encoded = URLEncoder.encode(text, Charsets.UTF_8.name())
            val url = URL("https://api.telegram.org/bot$token/sendMessage?chat_id=$chatId&text=$encoded")
            val conn = (url.openConnection() as HttpURLConnection)
            conn.requestMethod = "GET"
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            val code = conn.responseCode
            conn.disconnect()
            if (code !in 200..299) {
                Log.w(TAG, "telegram response code=$code")
            }
        }
    }

    private companion object {
        const val TAG = "TelegramAlert"
    }
}
