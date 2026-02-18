package com.dveamer.babysitter.soothing

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.dveamer.babysitter.settings.SettingsRepository
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class MusicSoothingListener(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    override val id: String = "music"
) : SoothingListener {

    private val lock = Mutex()
    @Volatile
    private var isPlaying = false
    @Volatile
    private var nextAllowedAttemptAtMs: Long = 0L

    override suspend fun soothe(request: SootheRequest): SootheResult {
        Log.d(TAG, "sooth : $request")
        if (isPlaying || request.requestedAtMs < nextAllowedAttemptAtMs) return SootheResult.IGNORED

        return lock.withLock {
            if (isPlaying || request.requestedAtMs < nextAllowedAttemptAtMs) {
                return@withLock SootheResult.IGNORED
            }

            val playlist = settingsRepository.state.value.musicPlaylist
            if (playlist.isEmpty()) return@withLock SootheResult.IGNORED

            isPlaying = true
            try {
                var playedCount = 0
                playlist.forEach { uriString ->
                    val uri = Uri.parse(uriString)
                    runCatching {
                        playOnce(uri)
                    }.onSuccess {
                        playedCount += 1
                    }.onFailure { t ->
                        Log.w(TAG, "track failed uri=$uri", t)
                    }
                }

                if (playedCount > 0) {
                    nextAllowedAttemptAtMs = 0L
                    SootheResult.STARTED
                } else {
                    nextAllowedAttemptAtMs = request.requestedAtMs + RETRY_BACKOFF_MS
                    SootheResult.FAILED
                }
            } catch (t: Throwable) {
                Log.w(TAG, "music soothing failed", t)
                nextAllowedAttemptAtMs = request.requestedAtMs + RETRY_BACKOFF_MS
                SootheResult.FAILED
            } finally {
                isPlaying = false
            }
        }
    }

    private suspend fun playOnce(uri: Uri): Unit = withContext(Dispatchers.IO) {
        withTimeout(PREPARE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                if (!SUPPORTED_URI_SCHEMES.contains(uri.scheme)) {
                    cont.resumeWithException(
                        IllegalArgumentException("unsupported uri scheme: ${uri.scheme}")
                    )
                    return@suspendCancellableCoroutine
                }

                val player = MediaPlayer()
                var fileInput: FileInputStream? = null
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )

                runCatching {
                    if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                        val file = File(uri.path!!)
                        if (!file.exists() || file.length() <= 0L) {
                            throw IllegalStateException("recording file invalid: ${file.absolutePath}")
                        }
                        fileInput = FileInputStream(file)
                        player.setDataSource(fileInput!!.fd)
                    } else {
                        player.setDataSource(context, uri)
                    }
                    player.setOnPreparedListener { mp ->
                        runCatching { mp.start() }.onFailure { e ->
                            runCatching { mp.release() }
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    }
                    player.setOnCompletionListener {
                        runCatching { fileInput?.close() }
                        fileInput = null
                        runCatching { it.release() }
                        if (cont.isActive) cont.resume(Unit)
                    }
                    player.setOnErrorListener { mp, _, _ ->
                        runCatching { fileInput?.close() }
                        fileInput = null
                        runCatching { mp.release() }
                        if (cont.isActive) cont.resumeWithException(IllegalStateException("media error"))
                        true
                    }
                    player.prepareAsync()
                }.onFailure { e ->
                    runCatching { fileInput?.close() }
                    fileInput = null
                    runCatching { player.release() }
                    if (cont.isActive) cont.resumeWithException(e)
                }

                cont.invokeOnCancellation {
                    runCatching {
                        runCatching { fileInput?.close() }
                        fileInput = null
                        if (player.isPlaying) player.stop()
                        player.release()
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "MusicSoothing"
        const val PREPARE_TIMEOUT_MS = 20_000L
        const val RETRY_BACKOFF_MS = 60_000L
        val SUPPORTED_URI_SCHEMES = setOf("http", "https", "content", "file", "android.resource")
    }
}
