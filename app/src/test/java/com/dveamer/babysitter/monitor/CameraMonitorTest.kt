package com.dveamer.babysitter.monitor

import com.dveamer.babysitter.collect.CollectFrameSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraMonitorTest {

    @Test
    fun `충분히 큰 gray 변화는 motion active로 본다`() {
        val monitor = CameraMonitor(
            scope = CoroutineScope(SupervisorJob()),
            diffThreshold = 20,
            minChangedRatio = 0.02
        )

        val previous = frameWithBlockValue()
        val current = frameWithBlockValue(blockStartX = 20, blockStartY = 16, blockSize = 20, blockValue = 220)

        assertTrue(monitor.detectMovement(previous, current))
    }

    @Test
    fun `작은 변화만 있으면 motion active가 아니다`() {
        val monitor = CameraMonitor(
            scope = CoroutineScope(SupervisorJob()),
            diffThreshold = 20,
            minChangedRatio = 0.02
        )

        val previous = frameWithBlockValue()
        val current = frameWithBlockValue(blockStartX = 10, blockStartY = 10, blockSize = 4, blockValue = 220)

        assertFalse(monitor.detectMovement(previous, current))
    }

    @Test
    fun `오래된 frame은 stale로 처리한다`() {
        val monitor = CameraMonitor(scope = CoroutineScope(SupervisorJob()))

        val staleFrame = frameWithBlockValue(capturedAtMs = System.currentTimeMillis() - 3_000L)

        assertTrue(monitor.isStale(staleFrame))
    }

    private fun frameWithBlockValue(
        width: Int = 80,
        height: Int = 60,
        baseValue: Int = 30,
        blockStartX: Int = 0,
        blockStartY: Int = 0,
        blockSize: Int = 0,
        blockValue: Int = baseValue,
        capturedAtMs: Long = System.currentTimeMillis()
    ): CollectFrameSnapshot {
        val gray = IntArray(width * height) { baseValue }
        if (blockSize > 0) {
            val endX = (blockStartX + blockSize).coerceAtMost(width)
            val endY = (blockStartY + blockSize).coerceAtMost(height)
            for (y in blockStartY until endY) {
                for (x in blockStartX until endX) {
                    gray[y * width + x] = blockValue
                }
            }
        }
        return CollectFrameSnapshot(
            gray = gray,
            width = width,
            height = height,
            capturedAtMs = capturedAtMs
        )
    }
}
