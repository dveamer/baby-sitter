package com.dveamer.babysitter.sleep

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `새 awake 없이 periodic 연장은 세 번까지만 허용한다`() {
        var now = 10 * 60_000L
        val manager = WakeMemoryManager { now }
        manager.onAwakeSignal(now)

        assertNotNull(buildNextPeriodicRange(manager, ++now))
        repeat(WakeMemoryManager.MAX_CONSECUTIVE_EXTENSIONS) {
            now += WakeMemoryManager.PERIODIC_BUILD_INTERVAL_MS
            assertNotNull(buildNextPeriodicRange(manager, now))
        }

        assertEquals(
            WakeMemoryManager.MAX_CONSECUTIVE_EXTENSIONS,
            manager.snapshot().successfulExtensions
        )

        now += WakeMemoryManager.PERIODIC_BUILD_INTERVAL_MS
        assertNull(
            manager.onPeriodicCheck(
                latestClosedVideoEndMs = now,
                nowMs = now
            )
        )
        assertFalse(manager.isAwakeSessionActive())

        manager.onAwakeSignal(now + 1L)
        assertFalse(manager.isAwakeSessionActive())
    }

    @Test
    fun `passive 이후 새 awake가 오면 periodic 연장 횟수를 초기화한다`() {
        var now = 20 * 60_000L
        val manager = WakeMemoryManager { now }
        manager.onAwakeSignal(now)

        assertNotNull(buildNextPeriodicRange(manager, ++now))
        repeat(2) {
            now += WakeMemoryManager.PERIODIC_BUILD_INTERVAL_MS
            assertNotNull(buildNextPeriodicRange(manager, now))
        }
        assertEquals(2, manager.snapshot().successfulExtensions)

        now += 1L
        manager.onPassiveSignal(lullabyActive = true, nowMs = now)
        now += 1L
        manager.onAwakeSignal(now)

        assertEquals(0, manager.snapshot().successfulExtensions)
        assertTrue(manager.isAwakeSessionActive())

        repeat(WakeMemoryManager.MAX_CONSECUTIVE_EXTENSIONS) {
            now += WakeMemoryManager.PERIODIC_BUILD_INTERVAL_MS
            assertNotNull(buildNextPeriodicRange(manager, now))
        }

        now += WakeMemoryManager.PERIODIC_BUILD_INTERVAL_MS
        assertNull(
            manager.onPeriodicCheck(
                latestClosedVideoEndMs = now,
                nowMs = now
            )
        )
        assertFalse(manager.isAwakeSessionActive())
    }

    @Test
    fun `같은 awake의 반복 신호는 periodic 연장 횟수를 초기화하지 않는다`() {
        var now = 30 * 60_000L
        val manager = WakeMemoryManager { now }
        manager.onAwakeSignal(now)

        assertNotNull(buildNextPeriodicRange(manager, ++now))
        now += WakeMemoryManager.PERIODIC_BUILD_INTERVAL_MS
        assertNotNull(buildNextPeriodicRange(manager, now))
        assertEquals(1, manager.snapshot().successfulExtensions)

        manager.onAwakeSignal(now + 1L)

        assertEquals(1, manager.snapshot().successfulExtensions)
    }

    @Test
    fun `세 번 연장한 뒤 stable 종료 시점에도 네 번째 저장은 만들지 않는다`() {
        var now = 40 * 60_000L
        val manager = WakeMemoryManager { now }
        manager.onAwakeSignal(now)

        assertNotNull(buildNextPeriodicRange(manager, ++now))
        repeat(WakeMemoryManager.MAX_CONSECUTIVE_EXTENSIONS) {
            now += WakeMemoryManager.PERIODIC_BUILD_INTERVAL_MS
            assertNotNull(buildNextPeriodicRange(manager, now))
        }

        now += 1L
        assertNull(manager.onPassiveSignal(lullabyActive = false, nowMs = now))
        now += WakeMemoryManager.SLEEP_STABLE_REQUIRED_MS

        assertNull(manager.onPassiveSignal(lullabyActive = false, nowMs = now))
        assertFalse(manager.isAwakeSessionActive())
    }

    private fun buildNextPeriodicRange(
        manager: WakeMemoryManager,
        nowMs: Long
    ): WakeMemoryTrigger? {
        val trigger = manager.onPeriodicCheck(
            latestClosedVideoEndMs = nowMs,
            nowMs = nowMs
        ) ?: return null
        manager.markMemoryBuildFinished(
            CoordinatedMemoryBuildResult(
                outputFile = File("/tmp/periodic-${trigger.requestedRangeEndMs}.mp4"),
                usedVideoFiles = 1,
                usedAudioFiles = 1,
                rangeStartMs = trigger.awakeStartedAt - WakeMemoryManager.PRE_ROLL_MS,
                requestedRangeEndMs = trigger.requestedRangeEndMs,
                effectiveRangeEndMs = nowMs
            )
        )
        return trigger
    }
}
