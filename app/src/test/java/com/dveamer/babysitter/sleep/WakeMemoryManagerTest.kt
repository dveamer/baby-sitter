package com.dveamer.babysitter.sleep

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WakeMemoryManagerTest {

    @Test
    fun `awake 이후 lullaby off 3분 지속 시 memory trigger`() {
        var now = 1_000L
        val manager = WakeMemoryManager { now }

        manager.onAwakeSignal(now)
        now += 10_000L
        manager.onAwakeSignal(now)

        now += 60_000L
        assertNull(manager.onPassiveSignal(lullabyActive = false, nowMs = now))

        now += WakeMemoryManager.SLEEP_STABLE_REQUIRED_MS
        val trigger = manager.onPassiveSignal(lullabyActive = false, nowMs = now)

        assertNotNull(trigger)
        assertEquals(1_000L, trigger?.awakeStartedAt)
        assertEquals(now, trigger?.requestedRangeEndMs)
    }

    @Test
    fun `lullaby active면 stable 타이머 초기화`() {
        var now = 5_000L
        val manager = WakeMemoryManager { now }

        manager.onAwakeSignal(now)

        now += 60_000L
        assertNull(manager.onPassiveSignal(lullabyActive = false, nowMs = now))

        now += 60_000L
        assertNull(manager.onPassiveSignal(lullabyActive = true, nowMs = now))

        now += WakeMemoryManager.SLEEP_STABLE_REQUIRED_MS
        assertNull(manager.onPassiveSignal(lullabyActive = false, nowMs = now))
    }

    @Test
    fun `periodic build는 최신 닫힌 collect 범위가 늘어났을 때만 1분 간격으로 재시도`() {
        var now = 10 * 60_000L
        val manager = WakeMemoryManager { now }
        val firstClosedEndMs = now + 59_999L
        val secondClosedEndMs = firstClosedEndMs + 60_000L

        manager.onAwakeSignal(now)

        now += WakeMemoryManager.PERIODIC_BUILD_INTERVAL_MS
        val firstTrigger = manager.onPeriodicCheck(
            latestClosedVideoEndMs = firstClosedEndMs,
            nowMs = now
        )

        assertNotNull(firstTrigger)
        manager.markMemoryBuildFinished(
            CoordinatedMemoryBuildResult(
                outputFile = File("/tmp/periodic-1.mp4"),
                usedVideoFiles = 2,
                usedAudioFiles = 1,
                rangeStartMs = firstTrigger!!.awakeStartedAt - WakeMemoryManager.PRE_ROLL_MS,
                requestedRangeEndMs = firstTrigger.requestedRangeEndMs,
                effectiveRangeEndMs = firstClosedEndMs
            )
        )

        assertNull(
            manager.onPeriodicCheck(
                latestClosedVideoEndMs = firstClosedEndMs,
                nowMs = now + WakeMemoryManager.PERIODIC_BUILD_INTERVAL_MS
            )
        )
        assertNull(
            manager.onPeriodicCheck(
                latestClosedVideoEndMs = secondClosedEndMs,
                nowMs = now + 30_000L
            )
        )

        now += WakeMemoryManager.PERIODIC_BUILD_INTERVAL_MS
        val secondTrigger = manager.onPeriodicCheck(
            latestClosedVideoEndMs = secondClosedEndMs,
            nowMs = now
        )

        assertNotNull(secondTrigger)
    }

    @Test
    fun `stable trigger 이후에도 목표 end를 따라잡을 때까지 세션 유지`() {
        var now = 5 * 60_000L
        val manager = WakeMemoryManager { now }

        manager.onAwakeSignal(now)

        now += 60_000L
        assertNull(manager.onPassiveSignal(lullabyActive = false, nowMs = now))

        now += WakeMemoryManager.SLEEP_STABLE_REQUIRED_MS
        val stableTrigger = manager.onPassiveSignal(lullabyActive = false, nowMs = now)

        assertNotNull(stableTrigger)
        manager.markMemoryBuildFinished(
            CoordinatedMemoryBuildResult(
                outputFile = File("/tmp/stable-partial.mp4"),
                usedVideoFiles = 3,
                usedAudioFiles = 1,
                rangeStartMs = stableTrigger!!.awakeStartedAt - WakeMemoryManager.PRE_ROLL_MS,
                requestedRangeEndMs = stableTrigger.requestedRangeEndMs,
                effectiveRangeEndMs = stableTrigger.requestedRangeEndMs - 60_000L
            )
        )
        assertNotNull(manager.snapshot().awakeStartedAt)

        now += WakeMemoryManager.PERIODIC_BUILD_INTERVAL_MS
        val periodicTrigger = manager.onPeriodicCheck(
            latestClosedVideoEndMs = now,
            nowMs = now
        )

        assertNotNull(periodicTrigger)
        manager.markMemoryBuildFinished(
            CoordinatedMemoryBuildResult(
                outputFile = File("/tmp/stable-final.mp4"),
                usedVideoFiles = 4,
                usedAudioFiles = 1,
                rangeStartMs = periodicTrigger!!.awakeStartedAt - WakeMemoryManager.PRE_ROLL_MS,
                requestedRangeEndMs = periodicTrigger.requestedRangeEndMs,
                effectiveRangeEndMs = now
            )
        )

        assertNull(manager.snapshot().awakeStartedAt)
        assertEquals(false, manager.isAwakeSessionActive())
    }
}
