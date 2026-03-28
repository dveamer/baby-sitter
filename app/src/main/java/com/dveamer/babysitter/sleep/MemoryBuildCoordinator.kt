package com.dveamer.babysitter.sleep

import android.util.Log
import com.dveamer.babysitter.collect.CollectCatalog
import com.dveamer.babysitter.collect.CollectClosedFileBus
import com.dveamer.babysitter.collect.CollectFileNaming
import com.dveamer.babysitter.collect.CollectFileType
import com.dveamer.babysitter.collect.TimedFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CoordinatedMemoryBuildResult(
    val outputFile: java.io.File?,
    val usedVideoFiles: Int,
    val usedAudioFiles: Int,
    val skippedReason: String? = null,
    val rangeStartMs: Long,
    val requestedRangeEndMs: Long,
    val effectiveRangeEndMs: Long? = null
)

class MemoryBuildCoordinator(
    private val buildMemory: (MemoryBuildRequest) -> MemoryBuildResult,
    private val listCollectVideos: () -> List<TimedFile>,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val waitFor: suspend (Long) -> Unit = { delay(it) }
) {
    private val buildMutex = Mutex()
    private val manualRequestMutex = Mutex()
    private val buildInProgress = AtomicBoolean(false)
    private val manualRequestInProgress = AtomicBoolean(false)

    fun isBuildInProgress(): Boolean = buildInProgress.get()

    fun isManualRequestInProgress(): Boolean = manualRequestInProgress.get()

    fun isManualCameraMemoryAvailable(): Boolean {
        return !buildInProgress.get() && !manualRequestInProgress.get()
    }

    suspend fun buildWakeMemory(trigger: WakeMemoryTrigger): CoordinatedMemoryBuildResult {
        val rangeStartMs = trigger.awakeStartedAt - WakeMemoryManager.PRE_ROLL_MS
        val requestedRangeEndMs = trigger.requestedRangeEndMs
        waitUntilBuildable(
            rangeStartMs = rangeStartMs,
            requestedRangeEndMs = requestedRangeEndMs,
            requireRequestedEnd = false,
            maxWaitMs = AUTO_BUILD_MAX_WAIT_MS,
            pollIntervalMs = AUTO_BUILD_POLL_MS
        )
        return build(rangeStartMs, requestedRangeEndMs, requireRequestedEnd = false)
    }

    suspend fun buildManualCameraMemory(requestedAtMs: Long = clock()): CoordinatedMemoryBuildResult {
        if (!manualRequestMutex.tryLock()) {
            return CoordinatedMemoryBuildResult(
                outputFile = null,
                usedVideoFiles = 0,
                usedAudioFiles = 0,
                skippedReason = SKIP_MANUAL_ALREADY_PENDING,
                rangeStartMs = requestedAtMs - MANUAL_PRE_ROLL_MS,
                requestedRangeEndMs = requestedAtMs + MANUAL_POST_ROLL_MS,
                effectiveRangeEndMs = resolveLatestClosedVideoEndMs()
            )
        }

        try {
            manualRequestInProgress.set(true)
            val rangeStartMs = requestedAtMs - MANUAL_PRE_ROLL_MS
            val requestedRangeEndMs = requestedAtMs + MANUAL_POST_ROLL_MS
            val ready = waitUntilBuildable(
                rangeStartMs = rangeStartMs,
                requestedRangeEndMs = requestedRangeEndMs,
                requireRequestedEnd = true,
                maxWaitMs = MANUAL_BUILD_MAX_WAIT_MS,
                pollIntervalMs = MANUAL_BUILD_POLL_MS
            )
            if (!ready) {
                return CoordinatedMemoryBuildResult(
                    outputFile = null,
                    usedVideoFiles = 0,
                    usedAudioFiles = 0,
                    skippedReason = SKIP_RANGE_NOT_READY,
                    rangeStartMs = rangeStartMs,
                    requestedRangeEndMs = requestedRangeEndMs,
                    effectiveRangeEndMs = resolveLatestClosedVideoEndMs()
                )
            }
            return build(rangeStartMs, requestedRangeEndMs, requireRequestedEnd = true)
        } finally {
            manualRequestInProgress.set(false)
            manualRequestMutex.unlock()
        }
    }

    private suspend fun waitUntilBuildable(
        rangeStartMs: Long,
        requestedRangeEndMs: Long,
        requireRequestedEnd: Boolean,
        maxWaitMs: Long,
        pollIntervalMs: Long
    ): Boolean {
        val deadlineMs = clock() + maxWaitMs
        while (true) {
            val latestClosedEndMs = resolveLatestClosedVideoEndMs()
            val effectiveRangeEndMs = when {
                latestClosedEndMs == null -> null
                requireRequestedEnd -> requestedRangeEndMs.takeIf { latestClosedEndMs >= it }
                else -> minOf(requestedRangeEndMs, latestClosedEndMs)
            }
            if (effectiveRangeEndMs != null && effectiveRangeEndMs >= rangeStartMs) {
                return true
            }
            val remainingMs = deadlineMs - clock()
            if (remainingMs <= 0L) {
                return false
            }
            waitFor(minOf(pollIntervalMs, remainingMs))
        }
    }

    private suspend fun build(
        rangeStartMs: Long,
        requestedRangeEndMs: Long,
        requireRequestedEnd: Boolean
    ): CoordinatedMemoryBuildResult {
        return buildMutex.withLock {
            val effectiveRangeEndMs = resolveEffectiveRangeEndMs(
                requestedRangeEndMs = requestedRangeEndMs,
                requireRequestedEnd = requireRequestedEnd
            )
            if (effectiveRangeEndMs == null || effectiveRangeEndMs < rangeStartMs) {
                return@withLock CoordinatedMemoryBuildResult(
                    outputFile = null,
                    usedVideoFiles = 0,
                    usedAudioFiles = 0,
                    skippedReason = if (requireRequestedEnd) SKIP_RANGE_NOT_READY else SKIP_NO_CLOSED_VIDEO_RANGE,
                    rangeStartMs = rangeStartMs,
                    requestedRangeEndMs = requestedRangeEndMs,
                    effectiveRangeEndMs = effectiveRangeEndMs
                )
            }

            buildInProgress.set(true)
            SleepRuntimeStatusStore.setMemoryBuildInProgress(true)
            try {
                val result = runCatching {
                    buildMemory(MemoryBuildRequest(rangeStartMs = rangeStartMs, rangeEndMs = effectiveRangeEndMs))
                }.onFailure { e ->
                    Log.w(TAG, "memory build failed start=$rangeStartMs end=$effectiveRangeEndMs", e)
                }.getOrNull()

                if (result?.outputFile != null) {
                    SleepRuntimeStatusStore.setLastMemoryBuiltAt(clock())
                }

                return@withLock if (result == null) {
                    CoordinatedMemoryBuildResult(
                        outputFile = null,
                        usedVideoFiles = 0,
                        usedAudioFiles = 0,
                        skippedReason = SKIP_BUILD_FAILED,
                        rangeStartMs = rangeStartMs,
                        requestedRangeEndMs = requestedRangeEndMs,
                        effectiveRangeEndMs = effectiveRangeEndMs
                    )
                } else {
                    CoordinatedMemoryBuildResult(
                        outputFile = result.outputFile,
                        usedVideoFiles = result.usedVideoFiles,
                        usedAudioFiles = result.usedAudioFiles,
                        skippedReason = result.skippedReason,
                        rangeStartMs = rangeStartMs,
                        requestedRangeEndMs = requestedRangeEndMs,
                        effectiveRangeEndMs = effectiveRangeEndMs
                    )
                }
            } finally {
                buildInProgress.set(false)
                SleepRuntimeStatusStore.setMemoryBuildInProgress(false)
            }
        }
    }

    fun latestClosedVideoEndMs(nowMs: Long = clock()): Long? {
        return resolveLatestClosedVideoEndMs(nowMs)
    }

    private fun resolveEffectiveRangeEndMs(
        requestedRangeEndMs: Long,
        requireRequestedEnd: Boolean
    ): Long? {
        val latestClosedEndMs = latestClosedVideoEndMs() ?: return null
        return if (requireRequestedEnd) {
            requestedRangeEndMs.takeIf { latestClosedEndMs >= it }
        } else {
            minOf(requestedRangeEndMs, latestClosedEndMs)
        }
    }

    private fun resolveLatestClosedVideoEndMs(nowMs: Long = clock()): Long? {
        val latestClosedStartMs = listOfNotNull(
            CollectClosedFileBus.latest(CollectFileType.VIDEO)?.startMs,
            listCollectVideos()
                .lastOrNull { it.startMs < CollectFileNaming.minuteFloor(nowMs) && it.file.exists() && it.file.length() > 0L }
                ?.startMs
        ).maxOrNull() ?: return null
        return latestClosedStartMs + CLOSED_MINUTE_DURATION_MS
    }

    constructor(
        assembler: MemoryAssembler,
        catalog: CollectCatalog,
        clock: () -> Long = { System.currentTimeMillis() },
        waitFor: suspend (Long) -> Unit = { delay(it) }
    ) : this(
        buildMemory = assembler::build,
        listCollectVideos = catalog::listCollectVideosSorted,
        clock = clock,
        waitFor = waitFor
    )

    companion object {
        const val MANUAL_PRE_ROLL_MS = 2 * 60 * 1000L
        const val MANUAL_POST_ROLL_MS = 2 * 60 * 1000L

        const val SKIP_MANUAL_ALREADY_PENDING = "manual_request_in_progress"
        const val SKIP_RANGE_NOT_READY = "memory_range_not_ready"
        const val SKIP_NO_CLOSED_VIDEO_RANGE = "no_closed_video_range"
        const val SKIP_BUILD_FAILED = "memory_build_failed"

        private const val TAG = "MemoryBuildCoord"
        private const val CLOSED_MINUTE_DURATION_MS = 59_999L
        private const val AUTO_BUILD_POLL_MS = 15_000L
        private const val AUTO_BUILD_MAX_WAIT_MS = 45_000L
        private const val MANUAL_BUILD_POLL_MS = 5_000L
        private const val MANUAL_BUILD_MAX_WAIT_MS = 4 * 60 * 1000L
    }
}
