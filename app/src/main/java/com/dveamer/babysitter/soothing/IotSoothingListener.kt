package com.dveamer.babysitter.soothing

import android.util.Log
import com.dveamer.babysitter.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

class IotSoothingListener(
    private val settingsRepository: SettingsRepository,
    override val id: String = "iot"
) : SoothingListener {

    private val inFlight = AtomicBoolean(false)

    override suspend fun soothe(request: SootheRequest): SootheResult {
        if (!inFlight.compareAndSet(false, true)) return SootheResult.IGNORED

        return try {
            val endpoint = settingsRepository.state.value.iotEndpoint
            if (endpoint.isBlank()) return SootheResult.IGNORED

            withContext(Dispatchers.IO) {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection)
                conn.requestMethod = "POST"
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                conn.doOutput = true
                conn.outputStream.use { it.write("{\"event\":\"soothe\"}".toByteArray()) }
                val code = conn.responseCode
                conn.disconnect()
                if (code !in 200..299) {
                    throw IllegalStateException("iot status=$code")
                }
            }
            SootheResult.STARTED
        } catch (t: Throwable) {
            Log.w(TAG, "iot soothe failed", t)
            SootheResult.FAILED
        } finally {
            inFlight.set(false)
        }
    }

    private companion object {
        const val TAG = "IotSoothing"
    }
}
