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
    fun `20초 연속 active 전에는 awake 아님`() {
        val detector = ContinuousAwakeDetector { SettingsState() }
        val monitorId = "mic-1"
        val base = 1_000L

        repeat(20) { idx ->
            val ts = base + idx * 1_000L
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
    fun `20초 연속 active가 완성되면 awake true`() {
        val detector = ContinuousAwakeDetector { SettingsState() }
        val monitorId = "mic-1"
        val base = 1_000L

        repeat(20) { idx ->
            val ts = base + idx * 1_000L
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

        val triggerTs = base + 20 * 1_000L
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
        assertEquals(base, state.awakeSinceMs)
        assertEquals(monitorId, state.reason)
    }

    @Test
    fun `설정된 40초 연속 active가 완성되면 awake true`() {
        val detector = ContinuousAwakeDetector {
            SettingsState(awakeTriggerDelaySec = 40)
        }
        val monitorId = "mic-1"
        val base = 1_000L

        repeat(40) { idx ->
            val ts = base + idx * 1_000L
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

        val triggerTs = base + 40 * 1_000L
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
        assertEquals(base, state.awakeSinceMs)
        assertEquals(monitorId, state.reason)
    }

    @Test
    fun `active 신호가 3초 이상 끊기면 awake 연속성이 초기화된다`() {
        val detector = ContinuousAwakeDetector { SettingsState() }
        val monitorId = "mic-1"
        val base = 1_000L

        repeat(10) { idx ->
            val ts = base + idx * 1_000L
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

        val inactiveTs = base + 13_000L
        detector.onSignal(
            signal = MonitorSignal(
                monitorId = monitorId,
                kind = MonitorKind.MICROPHONE,
                active = false,
                timestampMs = inactiveTs
            ),
            nowMs = inactiveTs
        )

        assertNull(activeSince(detector)[monitorId])
        val resumed = detector.onSignal(
            signal = MonitorSignal(
                monitorId = monitorId,
                kind = MonitorKind.MICROPHONE,
                active = true,
                timestampMs = inactiveTs + 1_000L
            ),
            nowMs = inactiveTs + 1_000L
        )
        assertFalse(resumed.isAwake)
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
                monitorId = "camera-1",
                kind = MonitorKind.CAMERA,
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
