package com.dveamer.babysitter.sleep

import com.dveamer.babysitter.monitor.MonitorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraAwakeConfirmationControllerTest {

    @Test
    fun `카메라 단독 awake는 15초 추가 확인 후 확정한다`() {
        val controller = CameraAwakeConfirmationController()
        val cameraAwake = awake(
            sinceMs = 1_000L,
            kinds = setOf(MonitorKind.CAMERA)
        )

        val started = controller.onAwakeState(cameraAwake, nowMs = 20_000L)
        val waiting = controller.onAwakeState(cameraAwake, nowMs = 34_999L)
        val confirmed = controller.onAwakeState(cameraAwake, nowMs = 35_000L)

        assertFalse(started.awake.isAwake)
        assertTrue(started.tentative)
        assertFalse(waiting.awake.isAwake)
        assertTrue(confirmed.awake.isAwake)
        assertFalse(confirmed.tentative)
        assertEquals(1_000L, confirmed.awake.awakeSinceMs)
    }

    @Test
    fun `tentative 중 마이크 awake가 확인되면 즉시 확정한다`() {
        val controller = CameraAwakeConfirmationController()
        controller.onAwakeState(
            awake(sinceMs = 1_000L, kinds = setOf(MonitorKind.CAMERA)),
            nowMs = 20_000L
        )

        val confirmed = controller.onAwakeState(
            awake(
                sinceMs = 1_000L,
                kinds = setOf(MonitorKind.CAMERA, MonitorKind.MICROPHONE)
            ),
            nowMs = 21_000L
        )

        assertTrue(confirmed.awake.isAwake)
        assertFalse(confirmed.tentative)
        assertEquals(1_000L, confirmed.awake.awakeSinceMs)
    }

    @Test
    fun `마이크 단독 awake는 추가 지연 없이 확정한다`() {
        val controller = CameraAwakeConfirmationController()

        val confirmed = controller.onAwakeState(
            awake(sinceMs = 2_000L, kinds = setOf(MonitorKind.MICROPHONE)),
            nowMs = 20_000L
        )

        assertTrue(confirmed.awake.isAwake)
        assertFalse(confirmed.tentative)
    }

    @Test
    fun `카메라가 10초간 inactive면 tentative를 취소한다`() {
        val controller = CameraAwakeConfirmationController()
        val cameraAwake = awake(sinceMs = 1_000L, kinds = setOf(MonitorKind.CAMERA))
        controller.onAwakeState(cameraAwake, nowMs = 20_000L)

        val inactive = controller.onAwakeState(AwakeState(isAwake = false), nowMs = 21_000L)
        val cancelled = controller.onAwakeState(AwakeState(isAwake = false), nowMs = 31_000L)

        assertTrue(inactive.tentative)
        assertFalse(cancelled.tentative)

        val restarted = controller.onAwakeState(
            awake(sinceMs = 32_000L, kinds = setOf(MonitorKind.CAMERA)),
            nowMs = 32_000L
        )
        assertFalse(restarted.awake.isAwake)
        assertTrue(restarted.tentative)
    }

    private fun awake(
        sinceMs: Long,
        kinds: Set<MonitorKind>
    ): AwakeState {
        return AwakeState(
            isAwake = true,
            awakeSinceMs = sinceMs,
            reason = kinds.joinToString(",") { it.name.lowercase() },
            triggeredKinds = kinds
        )
    }
}
