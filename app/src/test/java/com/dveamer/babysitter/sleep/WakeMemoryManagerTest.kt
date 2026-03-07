package com.dveamer.babysitter.sleep

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
        assertEquals(now, trigger?.sleepStableEndedAt)
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
}
