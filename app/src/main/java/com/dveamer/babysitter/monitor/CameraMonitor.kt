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
    private var settlingController = CameraMotionSettlingController()

    override suspend fun start() {
        if (job != null) return

        job = scope.launch(Dispatchers.Default) {
            var previous: CollectFrameSnapshot? = null
            while (isActive) {
                val current = CollectFrameBus.latest()?.takeIf { !isStale(it) }
                val nowMs = System.currentTimeMillis()

                val observation = when {
                    current == null -> null
                    previous == null -> null
                    current.capturedAtMs == previous.capturedAtMs -> null
                    else -> analyzeMovement(previous, current)
                }
                val decision = if (observation != null) {
                    settlingController.onObservation(observation, nowMs)
                } else {
                    CameraMotionDecision(active = false)
                }

                mutableSignals.tryEmit(
                    MonitorSignal(
                        monitorId = id,
                        kind = MonitorKind.CAMERA,
                        active = decision.active,
                        timestampMs = nowMs,
                        resetAwakeAccumulation = decision.resetAwakeAccumulation
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
        settlingController = CameraMotionSettlingController()
    }

    internal fun detectMovement(
        prev: CollectFrameSnapshot,
        current: CollectFrameSnapshot
    ): Boolean = analyzeMovement(prev, current).active

    internal fun analyzeMovement(
        prev: CollectFrameSnapshot,
        current: CollectFrameSnapshot
    ): CameraMotionObservation {
        if (prev.width != current.width || prev.height != current.height) {
            return CameraMotionObservation(active = false, changedRatio = 0.0, activeTileCount = 0)
        }
        val size = current.gray.size
        if (size == 0) {
            return CameraMotionObservation(active = false, changedRatio = 0.0, activeTileCount = 0)
        }
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

        return CameraMotionObservation(
            active = changedPixels >= MIN_CHANGED_PIXELS && changedRatio >= ratioThreshold,
            changedRatio = changedRatio,
            activeTileCount = countActiveTiles(cleaned, current.width, current.height)
        )
    }

    private fun countActiveTiles(src: IntArray, width: Int, height: Int): Int {
        var activeTiles = 0
        for (tileY in 0 until TILE_ROWS) {
            val startY = tileY * height / TILE_ROWS
            val endY = (tileY + 1) * height / TILE_ROWS
            for (tileX in 0 until TILE_COLUMNS) {
                val startX = tileX * width / TILE_COLUMNS
                val endX = (tileX + 1) * width / TILE_COLUMNS
                val tileSize = (endX - startX) * (endY - startY)
                if (tileSize <= 0) continue

                var changedPixels = 0
                for (y in startY until endY) {
                    for (x in startX until endX) {
                        changedPixels += src[y * width + x]
                    }
                }
                if (changedPixels.toDouble() / tileSize.toDouble() >= ACTIVE_TILE_CHANGED_RATIO) {
                    activeTiles += 1
                }
            }
        }
        return activeTiles
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
        const val TILE_COLUMNS = 4
        const val TILE_ROWS = 3
        const val ACTIVE_TILE_CHANGED_RATIO = 0.15
    }
}
