package com.dveamer.babysitter.sleep

import com.dveamer.babysitter.collect.CollectClosedFileBus
import com.dveamer.babysitter.collect.CollectClosedFileMeta
import com.dveamer.babysitter.collect.CollectFileType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryBuildCoordinatorTest {

    @Test
    fun `manual build waits until requested end minute is closed`() = runBlocking {
        CollectClosedFileBus.clear()
        SleepRuntimeStatusStore.reset()

        val requestedAtMs = 10 * 60_000L
        val requiredClosedStartMs = 12 * 60_000L
        var nowMs = requestedAtMs
        var builtRequest: MemoryBuildRequest? = null
        lateinit var coordinator: MemoryBuildCoordinator
        coordinator = MemoryBuildCoordinator(
            buildMemory = { request ->
                assertEquals(true, coordinator.isBuildInProgress())
                builtRequest = request
                MemoryBuildResult(
                    outputFile = File("/tmp/manual-memory.mp4"),
                    usedVideoFiles = 5,
                    usedAudioFiles = 0
                )
            },
            listCollectVideos = { emptyList() },
            clock = { nowMs },
            waitFor = { delayMs ->
                assertEquals(true, coordinator.isManualRequestInProgress())
                assertEquals(false, coordinator.isManualCameraMemoryAvailable())
                nowMs += delayMs
                if (nowMs >= requiredClosedStartMs + 60_000L) {
                    CollectClosedFileBus.publish(
                        CollectClosedFileMeta(
                            type = CollectFileType.VIDEO,
                            file = File("/tmp/collect_${requiredClosedStartMs}.mp4"),
                            startMs = requiredClosedStartMs,
                            closedAtMs = nowMs
                        )
                    )
                }
            }
        )

        val result = coordinator.buildManualCameraMemory(requestedAtMs)

        assertEquals(requestedAtMs - MemoryBuildCoordinator.MANUAL_PRE_ROLL_MS, builtRequest?.rangeStartMs)
        assertEquals(requestedAtMs + MemoryBuildCoordinator.MANUAL_POST_ROLL_MS, builtRequest?.rangeEndMs)
        assertEquals("/tmp/manual-memory.mp4", result.outputFile?.path)
        assertNull(result.skippedReason)
        assertEquals(false, coordinator.isManualRequestInProgress())
        assertEquals(false, coordinator.isBuildInProgress())
        assertEquals(true, coordinator.isManualCameraMemoryAvailable())
    }

    @Test
    fun `manual build times out when future collect files are not closed`() = runBlocking {
        CollectClosedFileBus.clear()
        SleepRuntimeStatusStore.reset()

        val requestedAtMs = 20 * 60_000L
        var nowMs = requestedAtMs
        var buildCalled = false
        val coordinator = MemoryBuildCoordinator(
            buildMemory = {
                buildCalled = true
                MemoryBuildResult(outputFile = File("/tmp/unexpected.mp4"), usedVideoFiles = 0, usedAudioFiles = 0)
            },
            listCollectVideos = { emptyList() },
            clock = { nowMs },
            waitFor = { delayMs -> nowMs += delayMs }
        )

        val result = coordinator.buildManualCameraMemory(requestedAtMs)

        assertEquals(MemoryBuildCoordinator.SKIP_RANGE_NOT_READY, result.skippedReason)
        assertNull(result.outputFile)
        assertEquals(false, buildCalled)
    }

    @Test
    fun `wake build uses latest closed range without waiting for requested end`() = runBlocking {
        CollectClosedFileBus.clear()
        SleepRuntimeStatusStore.reset()

        val trigger = WakeMemoryTrigger(
            awakeStartedAt = 30 * 60_000L,
            requestedRangeEndMs = (35 * 60_000L) + 10_000L
        )
        val latestClosedStartMs = 34 * 60_000L
        CollectClosedFileBus.publish(
            CollectClosedFileMeta(
                type = CollectFileType.VIDEO,
                file = File("/tmp/collect_${latestClosedStartMs}.mp4"),
                startMs = latestClosedStartMs,
                closedAtMs = latestClosedStartMs + 60_000L
            )
        )

        var builtRequest: MemoryBuildRequest? = null
        val coordinator = MemoryBuildCoordinator(
            buildMemory = { request ->
                builtRequest = request
                MemoryBuildResult(
                    outputFile = File("/tmp/auto-memory.mp4"),
                    usedVideoFiles = 4,
                    usedAudioFiles = 1
                )
            },
            listCollectVideos = { emptyList() }
        )

        val result = coordinator.buildWakeMemory(trigger)

        assertEquals(trigger.awakeStartedAt - WakeMemoryManager.PRE_ROLL_MS, builtRequest?.rangeStartMs)
        assertEquals(latestClosedStartMs + 59_999L, builtRequest?.rangeEndMs)
        assertEquals(latestClosedStartMs + 59_999L, result.effectiveRangeEndMs)
        assertNull(result.skippedReason)
    }

    @Test
    fun `latestClosedVideoEndMs는 catalog fallback도 사용한다`() {
        CollectClosedFileBus.clear()
        val latestClosedStartMs = 40 * 60_000L
        val coordinator = MemoryBuildCoordinator(
            buildMemory = {
                MemoryBuildResult(
                    outputFile = File("/tmp/unused.mp4"),
                    usedVideoFiles = 0,
                    usedAudioFiles = 0
                )
            },
            listCollectVideos = {
                listOf(
                    com.dveamer.babysitter.collect.TimedFile(
                        startMs = latestClosedStartMs,
                        file = File.createTempFile("collect-$latestClosedStartMs", ".mp4").apply {
                            writeBytes(ByteArray(2048))
                            deleteOnExit()
                        }
                    )
                )
            },
            clock = { latestClosedStartMs + 2 * 60_000L }
        )

        assertEquals(latestClosedStartMs + 59_999L, coordinator.latestClosedVideoEndMs())
    }

    @Test
    fun `manual camera memory is unavailable without recent closed video`() {
        CollectClosedFileBus.clear()

        val requestedAtMs = 15 * 60_000L
        val coordinator = MemoryBuildCoordinator(
            buildMemory = {
                MemoryBuildResult(
                    outputFile = File("/tmp/unused.mp4"),
                    usedVideoFiles = 0,
                    usedAudioFiles = 0
                )
            },
            listCollectVideos = { emptyList() },
            clock = { requestedAtMs }
        )

        assertFalse(coordinator.isManualCameraMemoryAvailable())
    }

    @Test
    fun `manual camera memory is available when closed video reaches preroll window`() {
        CollectClosedFileBus.clear()

        val requestedAtMs = 25 * 60_000L
        val latestClosedStartMs = 24 * 60_000L
        CollectClosedFileBus.publish(
            CollectClosedFileMeta(
                type = CollectFileType.VIDEO,
                file = File("/tmp/collect_$latestClosedStartMs.mp4"),
                startMs = latestClosedStartMs,
                closedAtMs = latestClosedStartMs + 60_000L
            )
        )
        val coordinator = MemoryBuildCoordinator(
            buildMemory = {
                MemoryBuildResult(
                    outputFile = File("/tmp/unused.mp4"),
                    usedVideoFiles = 0,
                    usedAudioFiles = 0
                )
            },
            listCollectVideos = { emptyList() },
            clock = { requestedAtMs }
        )

        assertTrue(coordinator.isManualCameraMemoryAvailable())
    }
}
