package com.dveamer.babysitter.monitor

import com.dveamer.babysitter.collect.CollectFrameBus
import com.dveamer.babysitter.collect.CollectFrameSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class CameraMonitor(
    private val scope: CoroutineScope,
    private val diffThreshold: Int = DEFAULT_DIFF_THRESHOLD,
    private val minChangedRatio: Double = DEFAULT_MIN_CHANGED_RATIO,
    override val id: String = "camera"
) : Monitor {

    private val mutableSignals = MutableSharedFlow<MonitorSignal>(extraBufferCapacity = 16)
    override val signals: Flow<MonitorSignal> = mutableSignals.asSharedFlow()

    private var job: Job? = null

    override suspend fun start() {
        if (job != null) return

        job = scope.launch(Dispatchers.Default) {
            var previous: CollectFrameSnapshot? = null
            while (isActive) {
                val current = CollectFrameBus.latest()?.takeIf { !isStale(it) }

                val active = when {
                    current == null -> false
                    previous == null -> false
                    current.capturedAtMs == previous.capturedAtMs -> false
                    else -> detectMovement(previous, current)
                }

                mutableSignals.tryEmit(
                    MonitorSignal(
                        monitorId = id,
                        kind = MonitorKind.CAMERA,
                        active = active
                    )
                )

                if (current != null) {
                    previous = current
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    override suspend fun stop() {
        job?.cancel()
        job = null
    }

    internal fun detectMovement(
        prev: CollectFrameSnapshot,
        current: CollectFrameSnapshot
    ): Boolean {
        if (prev.width != current.width || prev.height != current.height) return false
        val size = current.gray.size
        if (size == 0) return false
        val threshold = diffThreshold.coerceIn(1, 255)
        val ratioThreshold = minChangedRatio.coerceIn(0.001, 1.0)

        val binary = IntArray(size)
        for (i in 0 until size) {
            val diff = abs(current.gray[i] - prev.gray[i])
            binary[i] = if (diff >= threshold) 1 else 0
        }

        val opened = dilate(erode(binary, current.width, current.height), current.width, current.height)
        val cleaned = erode(dilate(opened, current.width, current.height), current.width, current.height)

        val changedPixels = cleaned.sum()
        val changedRatio = changedPixels.toDouble() / size.toDouble()

        return changedPixels >= MIN_CHANGED_PIXELS && changedRatio >= ratioThreshold
    }

    private fun erode(src: IntArray, width: Int, height: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var allOne = true
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny !in 0 until height) {
                        allOne = false
                        break
                    }
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx !in 0 until width || src[ny * width + nx] == 0) {
                            allOne = false
                            break
                        }
                    }
                    if (!allOne) break
                }
                out[y * width + x] = if (allOne) 1 else 0
            }
        }
        return out
    }

    private fun dilate(src: IntArray, width: Int, height: Int): IntArray {
        val out = IntArray(src.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var anyOne = false
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny !in 0 until height) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx !in 0 until width) continue
                        if (src[ny * width + nx] == 1) {
                            anyOne = true
                            break
                        }
                    }
                    if (anyOne) break
                }
                out[y * width + x] = if (anyOne) 1 else 0
            }
        }
        return out
    }

    internal fun isStale(snapshot: CollectFrameSnapshot): Boolean {
        return System.currentTimeMillis() - snapshot.capturedAtMs > STALE_TIMEOUT_MS
    }

    private companion object {
        const val DEFAULT_DIFF_THRESHOLD = 20
        const val MIN_CHANGED_PIXELS = 120
        const val DEFAULT_MIN_CHANGED_RATIO = 0.03
        const val STALE_TIMEOUT_MS = 2_000L
        const val POLL_INTERVAL_MS = 1_000L
    }
}
