package com.dveamer.babysitter.web

import android.content.Context
import android.util.Log
import com.dveamer.babysitter.settings.SettingsRepository
import com.dveamer.babysitter.settings.SettingsState
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
    private val settingsRepository: SettingsRepository
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

    private fun handleClient(client: Socket) {
        client.use { socket ->
            runCatching {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine().orEmpty()
                // Consume headers.
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }

                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0).orEmpty()
                val target = parts.getOrNull(1).orEmpty()
                val path = target.substringBefore('?')
                when {
                    method != "GET" -> {
                        writeResponse(
                            socket = socket,
                            code = 405,
                            status = "Method Not Allowed",
                            body = """{"error":"method_not_allowed"}"""
                        )
                    }

                    path == "/settings" -> {
                        writeResponse(
                            socket = socket,
                            code = 200,
                            status = "OK",
                            body = settingsToJson(settingsRepository.state.value)
                        )
                    }

                    path == "/index.html" || path == "/" -> {
                        val html = loadIndexHtml()
                        writeTextResponse(
                            socket = socket,
                            code = 200,
                            status = "OK",
                            contentType = "text/html; charset=utf-8",
                            body = html
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
