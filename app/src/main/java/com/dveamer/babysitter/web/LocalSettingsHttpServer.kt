package com.dveamer.babysitter.web

import android.content.Context
import android.util.Log
import com.dveamer.babysitter.settings.SettingsController
import com.dveamer.babysitter.settings.SettingsPatch
import com.dveamer.babysitter.settings.SettingsRepository
import com.dveamer.babysitter.settings.SettingsState
import com.dveamer.babysitter.settings.UpdateSource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class LocalSettingsHttpServer(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val settingsController: SettingsController
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptJob: Job? = null
    private var serverSocket: ServerSocket? = null

    suspend fun start() {
        if (acceptJob?.isActive == true) return
        val socket = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(PORT))
            }
        }.getOrElse { e ->
            Log.w(TAG, "Web service start failed on port=$PORT", e)
            return
        }
        serverSocket = socket
        acceptJob = scope.launch {
            Log.i(TAG, "Web service started on port=$PORT")
            while (isActive) {
                val client = runCatching { socket.accept() }.getOrElse { e ->
                    if (isActive) Log.w(TAG, "accept failed", e)
                    return@launch
                }
                launch {
                    handleClient(client)
                }
            }
        }
    }

    suspend fun stop() {
        serverSocket?.close()
        serverSocket = null
        acceptJob?.cancelAndJoin()
        acceptJob = null
        Log.i(TAG, "Web service stopped")
    }

    private suspend fun handleClient(client: Socket) {
        client.use { socket ->
            runCatching {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine().orEmpty()
                val headers = mutableMapOf<String, String>()
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                    val sep = line.indexOf(':')
                    if (sep > 0) {
                        val key = line.substring(0, sep).trim().lowercase()
                        val value = line.substring(sep + 1).trim()
                        headers[key] = value
                    }
                }

                val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                val body = if (contentLength > 0) {
                    val buffer = CharArray(contentLength)
                    var offset = 0
                    while (offset < contentLength) {
                        val read = reader.read(buffer, offset, contentLength - offset)
                        if (read < 0) break
                        offset += read
                    }
                    String(buffer, 0, offset)
                } else {
                    ""
                }

                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0).orEmpty()
                val target = parts.getOrNull(1).orEmpty()
                val path = target.substringBefore('?')
                when {
                    method == "GET" && path == "/settings" -> {
                        writeResponse(
                            socket = socket,
                            code = 200,
                            status = "OK",
                            body = settingsToJson(settingsRepository.state.value)
                        )
                    }

                    method == "PUT" && path == "/settings" -> {
                        val result = updateSettingsFromJson(body)
                        if (result) {
                            writeResponse(
                                socket = socket,
                                code = 200,
                                status = "OK",
                                body = settingsToJson(settingsRepository.state.value)
                            )
                        } else {
                            writeResponse(
                                socket = socket,
                                code = 400,
                                status = "Bad Request",
                                body = """{"error":"invalid_payload"}"""
                            )
                        }
                    }

                    method == "GET" && (path == "/index.html" || path == "/") -> {
                        val html = loadIndexHtml()
                        writeTextResponse(
                            socket = socket,
                            code = 200,
                            status = "OK",
                            contentType = "text/html; charset=utf-8",
                            body = html
                        )
                    }

                    method != "GET" && method != "PUT" -> {
                        writeResponse(
                            socket = socket,
                            code = 405,
                            status = "Method Not Allowed",
                            body = """{"error":"method_not_allowed"}"""
                        )
                    }

                    else -> {
                        writeResponse(
                            socket = socket,
                            code = 404,
                            status = "Not Found",
                            body = """{"error":"not_found"}"""
                        )
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "client handling failed", e)
            }
        }
    }

    private suspend fun updateSettingsFromJson(body: String): Boolean {
        return runCatching {
            val json = JSONObject(body)
            val patch = SettingsPatch(
                soundMonitoringEnabled = json.optBooleanOrNull("soundMonitoringEnabled"),
                cameraMonitoringEnabled = json.optBooleanOrNull("cameraMonitoringEnabled"),
                soothingMusicEnabled = json.optBooleanOrNull("soothingMusicEnabled"),
                wakeAlertThresholdMin = json.optIntOrNull("wakeAlertThresholdMin")
            )
            settingsController.update(patch, UpdateSource.REMOTE)
        }.getOrElse { e ->
            Log.w(TAG, "failed to update settings from payload", e)
            false
        }
    }

    private fun writeResponse(
        socket: Socket,
        code: Int,
        status: String,
        body: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $code $status\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        val output = socket.getOutputStream()
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun writeTextResponse(
        socket: Socket,
        code: Int,
        status: String,
        contentType: String,
        body: String
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("HTTP/1.1 $code $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }
        val output = socket.getOutputStream()
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(bytes)
        output.flush()
    }

    private fun loadIndexHtml(): String {
        return runCatching {
            appContext.assets.open("index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { e ->
            Log.w(TAG, "failed to load assets/index.html", e)
            "<html><body><h1>Baby Sitter Web Service</h1></body></html>"
        }
    }

    private fun settingsToJson(state: SettingsState): String {
        return JSONObject()
            .put("sleepEnabled", state.sleepEnabled)
            .put("soundMonitoringEnabled", state.soundMonitoringEnabled)
            .put("cryThresholdSec", state.cryThresholdSec)
            .put("movementThresholdSec", state.movementThresholdSec)
            .put("cameraMonitoringEnabled", state.cameraMonitoringEnabled)
            .put("soothingMusicEnabled", state.soothingMusicEnabled)
            .put("wakeAlertThresholdMin", state.wakeAlertThresholdMin)
            .toString()
    }

    companion object {
        private const val TAG = "LocalSettingsHttpServer"
        const val PORT = 8901
    }
}

private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
    return if (has(key) && !isNull(key)) optBoolean(key) else null
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    return if (has(key) && !isNull(key)) optInt(key) else null
}
