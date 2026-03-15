package com.dveamer.babysitter.sleep

import com.dveamer.babysitter.monitor.MonitorKind
import com.dveamer.babysitter.monitor.MonitorSignal
import com.dveamer.babysitter.settings.SettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousAwakeDetectorTest {

    @Test
    fun `20초 누적 창이 모이기 전에는 awake 아님`() {
        val detector = ContinuousAwakeDetector { SettingsState() }
        val monitorId = "mic-1"

        listOf(1_000L, 6_000L, 11_000L).forEach { ts ->
            val state = detector.onSignal(
                signal = activeSignal(monitorId, ts),
                nowMs = ts
            )
            assertFalse(state.isAwake)
        }

        val boundaryState = detector.onSignal(
            signal = inactiveSignal(monitorId, 15_000L),
            nowMs = 15_000L
        )

        assertFalse(boundaryState.isAwake)
    }

    @Test
    fun `4개 5초 창에 active가 있으면 awake true`() {
        val detector = ContinuousAwakeDetector { SettingsState() }
        val monitorId = "mic-1"

        listOf(1_000L, 6_000L, 11_000L, 16_000L).forEach { ts ->
            detector.onSignal(
                signal = activeSignal(monitorId, ts),
                nowMs = ts
            )
        }

        val state = detector.onSignal(
            signal = inactiveSignal(monitorId, 20_000L),
            nowMs = 20_000L
        )

        assertTrue(state.isAwake)
        assertEquals(0L, state.awakeSinceMs)
        assertEquals(monitorId, state.reason)
    }

    @Test
    fun `설정된 40초는 8개 창 누적 후 awake true`() {
        val detector = ContinuousAwakeDetector {
            SettingsState(awakeTriggerDelaySec = 40)
        }
        val monitorId = "mic-1"

        listOf(1_000L, 6_000L, 11_000L, 16_000L, 21_000L, 26_000L, 31_000L, 36_000L).forEach { ts ->
            detector.onSignal(
                signal = activeSignal(monitorId, ts),
                nowMs = ts
            )
        }

        val state = detector.onSignal(
            signal = inactiveSignal(monitorId, 40_000L),
            nowMs = 40_000L
        )

        assertTrue(state.isAwake)
        assertEquals(0L, state.awakeSinceMs)
        assertEquals(monitorId, state.reason)
    }

    @Test
    fun `중간 5초 창에 active가 없으면 누적이 초기화된다`() {
        val detector = ContinuousAwakeDetector { SettingsState() }
        val monitorId = "mic-1"

        detector.onSignal(signal = activeSignal(monitorId, 1_000L), nowMs = 1_000L)
        detector.onSignal(signal = activeSignal(monitorId, 6_000L), nowMs = 6_000L)
        detector.onSignal(signal = inactiveSignal(monitorId, 15_000L), nowMs = 15_000L)
        detector.onSignal(signal = activeSignal(monitorId, 16_000L), nowMs = 16_000L)
        detector.onSignal(signal = activeSignal(monitorId, 21_000L), nowMs = 21_000L)

        val state = detector.onSignal(
            signal = inactiveSignal(monitorId, 25_000L),
            nowMs = 25_000L
        )

        assertFalse(state.isAwake)
    }

    @Test
    fun `activeSince는 true 무신호 10분 후 제거된다`() {
        val detector = ContinuousAwakeDetector { SettingsState() }
        val monitorId = "mic-1"
        val base = 1_000L

        detector.onSignal(
            signal = activeSignal(monitorId, base),
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

    private fun activeSignal(monitorId: String, timestampMs: Long): MonitorSignal {
        return MonitorSignal(
            monitorId = monitorId,
            kind = MonitorKind.MICROPHONE,
            active = true,
            timestampMs = timestampMs
        )
    }

    private fun inactiveSignal(monitorId: String, timestampMs: Long): MonitorSignal {
        return MonitorSignal(
            monitorId = monitorId,
            kind = MonitorKind.MICROPHONE,
            active = false,
            timestampMs = timestampMs
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun activeSince(detector: ContinuousAwakeDetector): MutableMap<String, Long> {
        val field = ContinuousAwakeDetector::class.java.getDeclaredField("activeSince")
        field.isAccessible = true
        return field.get(detector) as MutableMap<String, Long>
    }
}
