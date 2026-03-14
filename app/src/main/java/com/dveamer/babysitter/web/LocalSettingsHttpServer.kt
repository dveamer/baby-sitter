package com.dveamer.babysitter.web

import android.content.Context
import android.util.Log
import com.dveamer.babysitter.collect.MemoryRepository
import com.dveamer.babysitter.monitor.CameraFrameBus
import com.dveamer.babysitter.monitor.CameraFrameSnapshot
import com.dveamer.babysitter.settings.MotionSensitivity
import com.dveamer.babysitter.settings.SoundSensitivity
import com.dveamer.babysitter.settings.SettingsController
import com.dveamer.babysitter.settings.SettingsPatch
import com.dveamer.babysitter.settings.SettingsRepository
import com.dveamer.babysitter.settings.SettingsState
import com.dveamer.babysitter.settings.ThemeMode
import com.dveamer.babysitter.settings.UpdateSource
import com.dveamer.babysitter.sleep.MemoryBuildCoordinator
import com.dveamer.babysitter.sleep.SleepRuntimeStatusStore
import java.io.BufferedReader
import java.io.EOFException
import java.io.InterruptedIOException
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
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
    private val settingsController: SettingsController,
    private val memoryRepository: MemoryRepository,
    private val memoryBuildCoordinator: MemoryBuildCoordinator,
    private val memoryDownloadLimiter: MemoryDownloadLimiter
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

                    method == "GET" && path == "/camera/stream" -> {
                        val state = settingsRepository.state.value
                        if (!state.webCameraEnabled) {
                            writeResponse(
                                socket = socket,
                                code = 403,
                                status = "Forbidden",
                                body = """{"error":"camera_disabled"}"""
                            )
                            return@runCatching
                        }
                        streamFromMotionCamera(socket)
                    }

                    method == "GET" && path == "/memory" -> {
                        val downloadSnapshot = memoryDownloadLimiter.currentSnapshot()
                        val memoryBody = JSONObject().put(
                            "items",
                            memoryRepository.listLatest().fold(org.json.JSONArray()) { arr, item ->
                                arr.put(
                                    JSONObject()
                                        .put("fileName", item.fileName)
                                        .put("startEpochMs", item.startEpochMs)
                                        .put("sizeBytes", item.sizeBytes)
                                        .put("durationMs", item.durationMs)
                                )
                                arr
                            }
                        )
                            .put("download", memoryDownloadSnapshotToJson(downloadSnapshot))
                            .toString()
                        writeResponse(
                            socket = socket,
                            code = 200,
                            status = "OK",
                            body = memoryBody
                        )
                    }

                    method == "POST" && path == "/memory/manual" -> {
                        val result = memoryBuildCoordinator.buildManualCameraMemory()
                        val fileName = result.outputFile?.name
                        val memoryItem = fileName?.let { name ->
                            memoryRepository.listLatest().firstOrNull { it.fileName == name }
                        }
                        val responseBody = JSONObject()
                            .put("fileName", fileName)
                            .put("error", result.skippedReason)
                            .put("rangeStartMs", result.rangeStartMs)
                            .put("requestedRangeEndMs", result.requestedRangeEndMs)
                            .put("effectiveRangeEndMs", result.effectiveRangeEndMs)
                            .put("skippedReason", result.skippedReason)
                            .apply {
                                if (memoryItem != null) {
                                    put("startEpochMs", memoryItem.startEpochMs)
                                    put("sizeBytes", memoryItem.sizeBytes)
                                    put("durationMs", memoryItem.durationMs)
                                }
                            }
                            .toString()
                        val (code, status) = when {
                            result.outputFile != null -> 201 to "Created"
                            result.skippedReason == MemoryBuildCoordinator.SKIP_MANUAL_ALREADY_PENDING -> 409 to "Conflict"
                            result.skippedReason == MemoryBuildCoordinator.SKIP_RANGE_NOT_READY -> 409 to "Conflict"
                            result.skippedReason == "no_video_collect" ||
                                result.skippedReason == "start_not_found" ||
                                result.skippedReason == "no_video_in_range" ||
                                result.skippedReason == MemoryBuildCoordinator.SKIP_NO_CLOSED_VIDEO_RANGE -> 422 to "Unprocessable Entity"

                            else -> 500 to "Internal Server Error"
                        }
                        writeResponse(
                            socket = socket,
                            code = code,
                            status = status,
                            body = responseBody
                        )
                    }

                    method == "GET" && path.startsWith("/memory-download/") -> {
                        val fileName = path.removePrefix("/memory-download/")
                        val file = memoryRepository.findByName(fileName)
                        if (file == null || !file.exists()) {
                            writeResponse(
                                socket = socket,
                                code = 404,
                                status = "Not Found",
                                body = """{"error":"memory_not_found"}"""
                            )
                            return@runCatching
                        }
                        val decision = memoryDownloadLimiter.tryConsume()
                        if (!decision.allowed) {
                            writeResponse(
                                socket = socket,
                                code = 429,
                                status = "Too Many Requests",
                                body = JSONObject()
                                    .put("error", ERROR_MEMORY_DOWNLOAD_DAILY_LIMIT_EXCEEDED)
                                    .put("download", memoryDownloadSnapshotToJson(decision.snapshot))
                                    .toString()
                            )
                            return@runCatching
                        }
                        writeMemoryStream(
                            socket = socket,
                            file = file,
                            rangeHeader = headers["range"],
                            contentDisposition = """attachment; filename="$fileName""""
                        )
                    }

                    method == "GET" && path.startsWith("/memory/") -> {
                        val fileName = path.removePrefix("/memory/")
                        val file = memoryRepository.findByName(fileName)
                        if (file == null || !file.exists()) {
                            writeResponse(
                                socket = socket,
                                code = 404,
                                status = "Not Found",
                                body = """{"error":"memory_not_found"}"""
                            )
                            return@runCatching
                        }
                        writeMemoryStream(socket, file, headers["range"])
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
                        val html = loadHtmlAsset(
                            assetName = "index.html",
                            fallbackHtml = "<html><body><h1>Baby Sitter Web Service</h1></body></html>"
                        )
                        writeTextResponse(
                            socket = socket,
                            code = 200,
                            status = "OK",
                            contentType = "text/html; charset=utf-8",
                            body = html
                        )
                    }

                    method != "GET" && method != "PUT" && method != "POST" -> {
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
                if (isClientDisconnect(e)) {
                    Log.d(TAG, "client disconnected during request handling")
                } else {
                    Log.w(TAG, "client handling failed", e)
                }
            }
        }
    }

    private suspend fun updateSettingsFromJson(body: String): Boolean {
        return runCatching {
            val json = JSONObject(body)
            val patch = SettingsPatch(
                sleepEnabled = json.optBooleanOrNull("sleepEnabled"),
                webCameraEnabled = json.optBooleanOrNull("webCameraEnabled"),
                soundMonitoringEnabled = json.optBooleanOrNull("soundMonitoringEnabled"),
                soundSensitivity = json.optEnumOrNull("soundSensitivity", SoundSensitivity::valueOf),
                cryThresholdSec = json.optIntOrNull("cryThresholdSec"),
                cameraMonitoringEnabled = json.optBooleanOrNull("cameraMonitoringEnabled"),
                motionSensitivity = json.optEnumOrNull("motionSensitivity", MotionSensitivity::valueOf),
                movementThresholdSec = json.optIntOrNull("movementThresholdSec"),
                soothingMusicEnabled = json.optBooleanOrNull("soothingMusicEnabled"),
                themeMode = json.optEnumOrNull("themeMode", ThemeMode::valueOf)
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

    private fun writeMemoryStream(
        socket: Socket,
        file: java.io.File,
        rangeHeader: String?,
        contentDisposition: String? = null
    ) {
        val fileLength = file.length()
        val (start, end, partial) = parseRange(rangeHeader, fileLength)
        val length = (end - start + 1).coerceAtLeast(0L)
        val header = buildString {
            append("HTTP/1.1 ${if (partial) "206 Partial Content" else "200 OK"}\r\n")
            append("Content-Type: video/mp4\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Content-Length: $length\r\n")
            if (!contentDisposition.isNullOrBlank()) {
                append("Content-Disposition: $contentDisposition\r\n")
            }
            if (partial) {
                append("Content-Range: bytes $start-$end/$fileLength\r\n")
            }
            append("Connection: close\r\n")
            append("\r\n")
        }
        val output = socket.getOutputStream()
        output.write(header.toByteArray(Charsets.UTF_8))

        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buffer = ByteArray(16 * 1024)
            var remaining = length
            while (remaining > 0) {
                val chunk = minOf(buffer.size.toLong(), remaining).toInt()
                val read = raf.read(buffer, 0, chunk)
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read.toLong()
            }
        }
        output.flush()
    }

    private fun parseRange(rangeHeader: String?, totalLength: Long): Triple<Long, Long, Boolean> {
        if (totalLength <= 0L) {
            return Triple(0L, -1L, false)
        }
        if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=")) {
            val end = (totalLength - 1L).coerceAtLeast(0L)
            return Triple(0L, end, false)
        }
        val range = rangeHeader.removePrefix("bytes=").substringBefore(",").trim()
        val startRaw = range.substringBefore("-").trim()
        val endRaw = range.substringAfter("-", "").trim()
        val start = startRaw.toLongOrNull() ?: 0L
        val end = (endRaw.toLongOrNull() ?: (totalLength - 1L)).coerceAtMost(totalLength - 1L)
        if (start < 0L || start > end) {
            return Triple(0L, (totalLength - 1L).coerceAtLeast(0L), false)
        }
        return Triple(start, end, true)
    }

    private fun loadHtmlAsset(assetName: String, fallbackHtml: String): String {
        return runCatching {
            appContext.assets.open(assetName).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse { e ->
            Log.w(TAG, "failed to load assets/$assetName", e)
            fallbackHtml
        }
    }

    private fun streamFromMotionCamera(socket: Socket) {
        try {
            val output = socket.getOutputStream()
            val headers = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: multipart/x-mixed-replace; boundary=$MOTION_BOUNDARY\r\n")
                append("Cache-Control: no-cache\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }
            output.write(headers.toByteArray(Charsets.UTF_8))
            output.flush()
            while (!socket.isClosed) {
                val state = settingsRepository.state.value
                if (!state.webCameraEnabled) break
                val frame = CameraFrameBus.latest()
                if (frame == null || isStale(frame)) {
                    Thread.sleep(MOTION_STREAM_WAIT_MS)
                    continue
                }
                val partHeader = buildString {
                    append("--$MOTION_BOUNDARY\r\n")
                    append("Content-Type: image/jpeg\r\n")
                    append("Content-Length: ${frame.jpeg.size}\r\n")
                    append("\r\n")
                }
                output.write(partHeader.toByteArray(Charsets.UTF_8))
                output.write(frame.jpeg)
                output.write("\r\n".toByteArray(Charsets.UTF_8))
                output.flush()
                Thread.sleep(MOTION_STREAM_INTERVAL_MS)
            }
        } catch (e: Throwable) {
            if (isClientDisconnect(e)) {
                Log.d(TAG, "motion camera stream client disconnected")
            } else {
                throw e
            }
        }
    }

    private fun isStale(frame: CameraFrameSnapshot): Boolean {
        return System.currentTimeMillis() - frame.capturedAtMs > MOTION_STREAM_STALE_MS
    }

    private fun memoryDownloadSnapshotToJson(snapshot: MemoryDownloadQuotaSnapshot): JSONObject {
        return JSONObject()
            .put("dailyLimit", snapshot.dailyLimit)
            .put("usedToday", snapshot.downloadsUsedToday)
            .put("remainingToday", snapshot.downloadsRemainingToday)
            .put("available", snapshot.downloadAvailableToday)
            .put("benefitActive", !snapshot.benefitProductId.isNullOrBlank())
            .put("benefitProductId", snapshot.benefitProductId)
            .put("benefitExpiresAtMs", snapshot.benefitExpiresAtEpochMs)
    }

    private fun settingsToJson(state: SettingsState): String {
        val runtime = SleepRuntimeStatusStore.state.value
        val memoryBuildInProgress = runtime.memoryBuildInProgress || memoryBuildCoordinator.isBuildInProgress()
        val manualMemoryRequestInProgress = memoryBuildCoordinator.isManualRequestInProgress()
        return JSONObject()
            .put("sleepEnabled", state.sleepEnabled)
            .put("webServiceEnabled", state.webServiceEnabled)
            .put("webCameraEnabled", state.webCameraEnabled)
            .put("soundMonitoringEnabled", state.soundMonitoringEnabled)
            .put("soundSensitivity", state.soundSensitivity.name)
            .put("cryThresholdSec", state.cryThresholdSec)
            .put("movementThresholdSec", state.movementThresholdSec)
            .put("cameraMonitoringEnabled", state.cameraMonitoringEnabled)
            .put("motionSensitivity", state.motionSensitivity.name)
            .put("soothingMusicEnabled", state.soothingMusicEnabled)
            .put("musicPlaylistCount", state.musicPlaylist.size)
            .put("themeMode", state.themeMode.name)
            .put("monitoringActive", runtime.monitoringActive)
            .put("lullabyActive", runtime.lullabyActive)
            .put("memoryBuildInProgress", memoryBuildInProgress)
            .put("manualMemoryRequestInProgress", manualMemoryRequestInProgress)
            .put("cameraMemoryAvailable", state.webCameraEnabled && memoryBuildCoordinator.isManualCameraMemoryAvailable())
            .put("lastMemoryBuiltAtMs", runtime.lastMemoryBuiltAtMs)
            .toString()
    }

    companion object {
        private const val TAG = "LocalSettingsHttpServer"
        private const val ERROR_MEMORY_DOWNLOAD_DAILY_LIMIT_EXCEEDED = "memory_download_daily_limit_exceeded"
        const val PORT = 8901
        private const val MOTION_BOUNDARY = "motion-frame"
        private const val MOTION_STREAM_INTERVAL_MS = 120L
        private const val MOTION_STREAM_WAIT_MS = 100L
        private const val MOTION_STREAM_STALE_MS = 2_000L
    }

    private fun isClientDisconnect(t: Throwable): Boolean {
        if (t is EOFException || t is InterruptedIOException) return true
        if (t is SocketException) {
            val msg = t.message?.lowercase().orEmpty()
            if (
                msg.contains("broken pipe") ||
                msg.contains("connection reset") ||
                msg.contains("socket closed") ||
                msg.contains("software caused connection abort")
            ) {
                return true
            }
        }
        return false
    }
}

private fun JSONObject.optBooleanOrNull(key: String): Boolean? {
    return if (has(key) && !isNull(key)) optBoolean(key) else null
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    return if (has(key) && !isNull(key)) optInt(key) else null
}

private inline fun <T> JSONObject.optEnumOrNull(
    key: String,
    parse: (String) -> T
): T? {
    if (!has(key) || isNull(key)) return null
    val raw = optString(key, "").trim()
    if (raw.isEmpty()) return null
    return runCatching { parse(raw.uppercase()) }.getOrNull()
}
