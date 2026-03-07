package com.dveamer.babysitter.soothing

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import com.dveamer.babysitter.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

class MusicSoothingListener(
    private val scope: CoroutineScope,
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val onPlaybackStateChanged: (Boolean) -> Unit = {},
    override val id: String = "music"
) : SoothingListener {

    private val lock = Mutex()
    @Volatile
    private var isPlaying = false
    @Volatile
    private var nextAllowedAttemptAtMs: Long = 0L
    @Volatile
    private var playbackJob: Job? = null
    @Volatile
    private var stopAfterCurrentTrackRequested = false
    @Volatile
    private var stopAfterCurrentTrackReason: String? = null

    override suspend fun soothe(request: SootheRequest): SootheResult {
        if (isPlaying) {
            Log.d(TAG, "ignore soothe: playback already active request=$request")
            return SootheResult.IGNORED
        }
        if (request.requestedAtMs < nextAllowedAttemptAtMs) {
            Log.d(
                TAG,
                "ignore soothe: retry backoff requestAt=${request.requestedAtMs} nextAllowed=$nextAllowedAttemptAtMs"
            )
            return SootheResult.IGNORED
        }

        return lock.withLock {
            if (isPlaying || playbackJob?.isActive == true || request.requestedAtMs < nextAllowedAttemptAtMs) {
                return@withLock SootheResult.IGNORED
            }

            val playlist = settingsRepository.state.value.musicPlaylist
            if (playlist.isEmpty()) {
                Log.w(TAG, "ignore soothe: playlist is empty request=$request")
                return@withLock SootheResult.IGNORED
            }

            isPlaying = true
            stopAfterCurrentTrackRequested = false
            stopAfterCurrentTrackReason = null
            onPlaybackStateChanged(true)
            playbackJob = scope.launch(Dispatchers.IO) {
                var playedCount = 0
                try {
                    for (index in playlist.indices) {
                        if (index > 0 && stopAfterCurrentTrackRequested) {
                            Log.d(
                                TAG,
                                "finish music soothing after current track reason=$stopAfterCurrentTrackReason"
                            )
                            break
                        }
                        val uriString = playlist[index]
                        val uri = Uri.parse(uriString)
                        try {
                            playOnce(uri)
                            playedCount += 1
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (t: Throwable) {
                            Log.w(TAG, "track failed uri=$uri", t)
                        }
                    }
                    if (playedCount == 0) {
                        nextAllowedAttemptAtMs = request.requestedAtMs + RETRY_BACKOFF_MS
                    } else {
                        nextAllowedAttemptAtMs = 0L
                    }
                } catch (cancelled: CancellationException) {
                    Log.d(TAG, "music soothing cancelled")
                    nextAllowedAttemptAtMs = 0L
                    throw cancelled
                } catch (t: Throwable) {
                    Log.w(TAG, "music soothing failed", t)
                    nextAllowedAttemptAtMs = request.requestedAtMs + RETRY_BACKOFF_MS
                } finally {
                    lock.withLock {
                        playbackJob = null
                        isPlaying = false
                        stopAfterCurrentTrackRequested = false
                        stopAfterCurrentTrackReason = null
                    }
                    onPlaybackStateChanged(false)
                }
            }

            Log.d(TAG, "music soothing started request=$request playlistSize=${playlist.size}")
            SootheResult.STARTED
        }
    }

    suspend fun stop(reason: String = "manual") {
        val job = lock.withLock {
            val current = playbackJob
            if (current != null) {
                Log.d(TAG, "stop music soothing reason=$reason")
            }
            stopAfterCurrentTrackRequested = false
            stopAfterCurrentTrackReason = null
            current
        } ?: return

        job.cancelAndJoin()
    }

    suspend fun stopAfterCurrentTrack(reason: String) {
        lock.withLock {
            if (playbackJob == null) return@withLock
            if (!stopAfterCurrentTrackRequested) {
                Log.d(TAG, "stop music soothing after current track reason=$reason")
            }
            stopAfterCurrentTrackRequested = true
            stopAfterCurrentTrackReason = reason
        }
    }

    suspend fun clearPendingStop(reason: String) {
        lock.withLock {
            if (!stopAfterCurrentTrackRequested) return@withLock
            Log.d(
                TAG,
                "clear music soothing stop request reason=$reason pending=$stopAfterCurrentTrackReason"
            )
            stopAfterCurrentTrackRequested = false
            stopAfterCurrentTrackReason = null
        }
    }

    private suspend fun playOnce(uri: Uri): Unit = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            if (!SUPPORTED_URI_SCHEMES.contains(uri.scheme)) {
                cont.resumeWithException(
                    IllegalArgumentException("unsupported uri scheme: ${uri.scheme}")
                )
                return@suspendCancellableCoroutine
            }

            val player = MediaPlayer()
            var fileInput: FileInputStream? = null
            val finished = AtomicBoolean(false)
            val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            var watchdogJob: Job? = null

            fun cleanup() {
                runCatching { fileInput?.close() }
                fileInput = null
                runCatching {
                    if (player.isPlaying) player.stop()
                }
                runCatching { player.release() }
            }

            fun finish(error: Throwable? = null) {
                if (!finished.compareAndSet(false, true)) return
                watchdogJob?.cancel()
                watchdogScope.cancel()
                cleanup()
                if (!cont.isActive) return
                if (error == null) {
                    cont.resume(Unit)
                } else {
                    cont.resumeWithException(error)
                }
            }

            fun armWatchdog(delayMs: Long, onTimeout: () -> Unit) {
                watchdogJob?.cancel()
                watchdogJob = watchdogScope.launch {
                    delay(delayMs)
                    if (!finished.get()) {
                        onTimeout()
                    }
                }
            }

            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )

            runCatching {
                if (uri.scheme == "file") {
                    val filePath = uri.path ?: throw IllegalStateException("file uri path is null: $uri")
                    val file = File(filePath)
                    if (!file.exists() || file.length() <= 0L) {
                        throw IllegalStateException("recording file invalid: ${file.absolutePath}")
                    }
                    fileInput = FileInputStream(file)
                    val input = fileInput ?: throw IllegalStateException("file input is not initialized")
                    player.setDataSource(input.fd)
                } else {
                    player.setDataSource(context, uri)
                }
                player.setOnPreparedListener { mp ->
                    val durationMs = runCatching { mp.duration.toLong() }.getOrDefault(-1L)
                    val playbackTimeoutMs = if (durationMs > 0L) {
                        durationMs + PLAYBACK_COMPLETION_GRACE_MS
                    } else {
                        UNKNOWN_DURATION_TIMEOUT_MS
                    }
                    runCatching { mp.start() }.onFailure { e ->
                        finish(e)
                    }.onSuccess {
                        armWatchdog(playbackTimeoutMs) {
                            Log.w(
                                TAG,
                                "playback watchdog fired uri=$uri durationMs=$durationMs timeoutMs=$playbackTimeoutMs"
                            )
                            finish()
                        }
                    }
                }
                player.setOnCompletionListener {
                    finish()
                }
                player.setOnErrorListener { _, what, extra ->
                    finish(IllegalStateException("media error what=$what extra=$extra uri=$uri"))
                    true
                }
                armWatchdog(PREPARE_TIMEOUT_MS) {
                    Log.w(TAG, "prepare watchdog fired uri=$uri")
                    finish(IllegalStateException("media prepare timeout uri=$uri"))
                }
                player.prepareAsync()
            }.onFailure { e ->
                finish(e)
            }

            cont.invokeOnCancellation {
                if (!finished.compareAndSet(false, true)) return@invokeOnCancellation
                watchdogJob?.cancel()
                watchdogScope.cancel()
                cleanup()
            }
        }
    }

    private companion object {
        const val TAG = "MusicSoothing"
        const val RETRY_BACKOFF_MS = 60_000L
        const val PREPARE_TIMEOUT_MS = 15_000L
        const val PLAYBACK_COMPLETION_GRACE_MS = 3_000L
        const val UNKNOWN_DURATION_TIMEOUT_MS = 5 * 60 * 1000L
        val SUPPORTED_URI_SCHEMES = setOf("http", "https", "content", "file", "android.resource")
    }
}
