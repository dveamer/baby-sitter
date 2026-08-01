package com.dveamer.babysitter.monitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrophoneMonitorTest {

    @Test
    fun `사용자 입력 임계값을 음량과 YAMNet 기준으로 변환한다`() {
        val low = SoundDetectionThresholds.fromUserThreshold(250)
        val medium = SoundDetectionThresholds.fromUserThreshold(500)
        val high = SoundDetectionThresholds.fromUserThreshold(750)

        assertEquals(187.5, low.amplitude, 0.001)
        assertEquals(0.25f, low.yamNetCryConfidence, 0.001f)
        assertEquals(375.0, medium.amplitude, 0.001)
        assertEquals(0.5f, medium.yamNetCryConfidence, 0.001f)
        assertEquals(562.5, high.amplitude, 0.001)
        assertEquals(0.75f, high.yamNetCryConfidence, 0.001f)
    }

    @Test
    fun `noise floor는 큰 spike에 천천히 반응한다`() {
        val updated = updateNoiseFloor(previous = 753.0, amplitude = 31_032.0)

        assertTrue(updated > 753.0)
        assertTrue(updated < 1_100.0)
    }

    @Test
    fun `noise floor는 완만한 상승에는 점진적으로 반응한다`() {
        val updated = updateNoiseFloor(previous = 1_000.0, amplitude = 1_100.0)

        assertEquals(1_008.0, updated, 0.001)
    }

    @Test
    fun `noise floor는 하강을 빠르게 반영한다`() {
        val updated = updateNoiseFloor(previous = 1_000.0, amplitude = 400.0)

        assertEquals(850.0, updated, 0.001)
    }

    @Test
    fun `울음 판정은 음량과 YAMNet 조건을 모두 요구한다`() {
        assertTrue(
            passesCombinedCryGate(
                amplitudeFresh = true,
                amplitudeActive = true,
                yamNetFresh = true,
                yamNetActive = true
            )
        )
        assertFalse(
            passesCombinedCryGate(
                amplitudeFresh = true,
                amplitudeActive = false,
                yamNetFresh = true,
                yamNetActive = true
            )
        )
        assertFalse(
            passesCombinedCryGate(
                amplitudeFresh = true,
                amplitudeActive = true,
                yamNetFresh = true,
                yamNetActive = false
            )
        )
    }

    @Test
    fun `오래된 음량 또는 YAMNet 결과는 울음 판정에서 제외한다`() {
        assertFalse(
            passesCombinedCryGate(
                amplitudeFresh = false,
                amplitudeActive = true,
                yamNetFresh = true,
                yamNetActive = true
            )
        )
        assertFalse(
            passesCombinedCryGate(
                amplitudeFresh = true,
                amplitudeActive = true,
                yamNetFresh = false,
                yamNetActive = true
            )
        )
    }

    @Test
    fun `울음 조건이 두 번 연속이면 active가 된다`() {
        val tracker = MicrophoneMonitor.CryActivityTracker()

        assertFalse(tracker.update(rawActive = true).active)
        assertTrue(tracker.update(rawActive = true).active)
    }

    @Test
    fun `울음 조건이 세 번 연속 해제되면 inactive가 된다`() {
        val tracker = MicrophoneMonitor.CryActivityTracker()
        tracker.update(rawActive = true)
        tracker.update(rawActive = true)

        assertTrue(tracker.update(rawActive = false).active)
        assertTrue(tracker.update(rawActive = false).active)
        assertFalse(tracker.update(rawActive = false).active)
    }

    @Test
    fun `최근 네 번 중 두 번 울음이면 중간 공백이 있어도 active가 된다`() {
        val tracker = MicrophoneMonitor.CryActivityTracker()

        assertFalse(tracker.update(rawActive = true).active)
        assertFalse(tracker.update(rawActive = false).active)
        assertTrue(tracker.update(rawActive = true).active)
    }

    @Test
    fun `최근 네 번 중 울음이 한 번뿐이면 active가 되지 않는다`() {
        val tracker = MicrophoneMonitor.CryActivityTracker()

        assertFalse(tracker.update(rawActive = true).active)
        repeat(3) {
            assertFalse(tracker.update(rawActive = false).active)
        }

        val transition = tracker.update(rawActive = false)

        assertFalse(transition.active)
        assertEquals(0, transition.activeEvidenceCount)
        assertEquals(4, transition.evidenceWindowSize)
    }

    @Test
    fun `상태 전이는 주기와 관계없이 debug log 대상이다`() {
        assertTrue(shouldLogLevel(pollCount = 2, activeChanged = true))
    }

    @Test
    fun `상태 전이가 없으면 summary 주기에서만 debug log를 남긴다`() {
        assertTrue(shouldLogLevel(pollCount = 30, activeChanged = false))
        assertFalse(shouldLogLevel(pollCount = 29, activeChanged = false))
    }

    private fun updateNoiseFloor(previous: Double, amplitude: Double): Double {
        val monitor = MicrophoneMonitor(scope = CoroutineScope(SupervisorJob()))
        val method = MicrophoneMonitor::class.java.getDeclaredMethod(
            "updateNoiseFloor",
            Double::class.javaPrimitiveType,
            Double::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(monitor, previous, amplitude) as Double
    }

    private fun shouldLogLevel(pollCount: Long, activeChanged: Boolean): Boolean {
        val monitor = MicrophoneMonitor(scope = CoroutineScope(SupervisorJob()))
        val method = MicrophoneMonitor::class.java.getDeclaredMethod(
            "shouldLogLevel",
            Long::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        return method.invoke(monitor, pollCount, activeChanged) as Boolean
    }
}
