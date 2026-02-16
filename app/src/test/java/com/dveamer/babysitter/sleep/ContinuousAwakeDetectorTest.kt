package com.dveamer.babysitter.sleep

import com.dveamer.babysitter.monitor.MonitorKind
import com.dveamer.babysitter.monitor.MonitorSignal
import com.dveamer.babysitter.settings.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousAwakeDetectorTest {

    @Test
    fun `12개 5초 윈도우 연속 active 전에는 awake 아님`() {
        val detector = ContinuousAwakeDetector { SettingsState() }
        val monitorId = "mic-1"
        val base = 1_000L

        repeat(12) { idx ->
            val ts = base + idx * 5_000L
            val state = detector.onSignal(
                signal = MonitorSignal(
                    monitorId = monitorId,
                    kind = MonitorKind.MICROPHONE,
                    active = true,
                    timestampMs = ts
                ),
                nowMs = ts
            )
            assertFalse(state.isAwake)
        }
    }

    @Test
    fun `12개 5초 윈도우가 완성되면 awake true`() {
        val detector = ContinuousAwakeDetector { SettingsState() }
        val monitorId = "mic-1"
        val base = 1_000L

        repeat(12) { idx ->
            val ts = base + idx * 5_000L
            detector.onSignal(
                signal = MonitorSignal(
                    monitorId = monitorId,
                    kind = MonitorKind.MICROPHONE,
                    active = true,
                    timestampMs = ts
                ),
                nowMs = ts
            )
        }

        val triggerTs = base + 12 * 5_000L
        val state = detector.onSignal(
            signal = MonitorSignal(
                monitorId = monitorId,
                kind = MonitorKind.MICROPHONE,
                active = true,
                timestampMs = triggerTs
            ),
            nowMs = triggerTs
        )

        assertTrue(state.isAwake)
        assertNotNull(state.awakeSinceMs)
        assertEquals(0L, state.awakeSinceMs)
        assertEquals(monitorId, state.reason)
    }

    @Test
    fun `activeSince는 true 무신호 10분 후 제거된다`() {
        val detector = ContinuousAwakeDetector { SettingsState() }
        val monitorId = "mic-1"
        val base = 1_000L

        detector.onSignal(
            signal = MonitorSignal(
                monitorId = monitorId,
                kind = MonitorKind.MICROPHONE,
                active = true,
                timestampMs = base
            ),
            nowMs = base
        )

        detector.onSignal(
            signal = MonitorSignal(
                monitorId = monitorId,
                kind = MonitorKind.MICROPHONE,
                active = false,
                timestampMs = base + (10 * 60 * 1_000L) - 1_000L
            ),
            nowMs = base + (10 * 60 * 1_000L) - 1_000L
        )
        assertEquals(base, activeSince(detector)[monitorId])

        detector.onSignal(
            signal = MonitorSignal(
                monitorId = monitorId,
                kind = MonitorKind.MICROPHONE,
                active = false,
                timestampMs = base + (10 * 60 * 1_000L)
            ),
            nowMs = base + (10 * 60 * 1_000L)
        )
        assertNull(activeSince(detector)[monitorId])
    }

    @Suppress("UNCHECKED_CAST")
    private fun activeSince(detector: ContinuousAwakeDetector): MutableMap<String, Long> {
        val field = ContinuousAwakeDetector::class.java.getDeclaredField("activeSince")
        field.isAccessible = true
        return field.get(detector) as MutableMap<String, Long>
    }
}
