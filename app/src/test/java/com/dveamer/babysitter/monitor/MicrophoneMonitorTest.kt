package com.dveamer.babysitter.monitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrophoneMonitorTest {

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
}
