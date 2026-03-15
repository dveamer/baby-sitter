package com.dveamer.babysitter.sleep

import org.junit.Assert.assertEquals
import org.junit.Test

class MicrophoneMusicControllerTest {

    @Test
    fun `설정 시간 전에는 lullaby start 하지 않는다`() {
        val controller = MicrophoneMusicController()

        val action = controller.onSignal(
            active = true,
            nowMs = 1_000L,
            requiredActiveDurationMs = 20_000L
        )

        assertEquals(MicrophoneMusicAction.NONE, action)
    }

    @Test
    fun `설정 시간이 지나면 lullaby start 한다`() {
        val controller = MicrophoneMusicController()

        controller.onSignal(
            active = true,
            nowMs = 1_000L,
            requiredActiveDurationMs = 20_000L
        )

        val action = controller.onSignal(
            active = true,
            nowMs = 21_000L,
            requiredActiveDurationMs = 20_000L
        )

        assertEquals(MicrophoneMusicAction.START, action)
    }

    @Test
    fun `같은 active 구간에서는 start를 반복하지 않는다`() {
        val controller = MicrophoneMusicController()

        assertEquals(
            MicrophoneMusicAction.NONE,
            controller.onSignal(active = true, nowMs = 1_000L, requiredActiveDurationMs = 20_000L)
        )
        assertEquals(
            MicrophoneMusicAction.START,
            controller.onSignal(active = true, nowMs = 21_000L, requiredActiveDurationMs = 20_000L)
        )
        assertEquals(
            MicrophoneMusicAction.NONE,
            controller.onSignal(active = true, nowMs = 22_000L, requiredActiveDurationMs = 20_000L)
        )
    }

    @Test
    fun `inactive가 들어오면 다음 active 구간에서 다시 시간을 센다`() {
        val controller = MicrophoneMusicController()

        assertEquals(
            MicrophoneMusicAction.NONE,
            controller.onSignal(active = true, nowMs = 1_000L, requiredActiveDurationMs = 20_000L)
        )
        assertEquals(
            MicrophoneMusicAction.NONE,
            controller.onSignal(active = false, nowMs = 5_000L, requiredActiveDurationMs = 20_000L)
        )

        val action = controller.onSignal(
            active = true,
            nowMs = 6_000L,
            requiredActiveDurationMs = 20_000L
        )

        assertEquals(MicrophoneMusicAction.NONE, action)
    }

    @Test
    fun `inactive가 유지되면 start하지 않는다`() {
        val controller = MicrophoneMusicController()

        controller.onSignal(active = true, nowMs = 1_000L, requiredActiveDurationMs = 20_000L)
        controller.onSignal(active = false, nowMs = 2_000L, requiredActiveDurationMs = 20_000L)
        val action = controller.onSignal(
            active = false,
            nowMs = 3_000L,
            requiredActiveDurationMs = 20_000L
        )

        assertEquals(MicrophoneMusicAction.NONE, action)
    }

    @Test
    fun `reset 이후에는 다시 start 가능`() {
        val controller = MicrophoneMusicController()

        controller.onSignal(active = true, nowMs = 1_000L, requiredActiveDurationMs = 20_000L)
        controller.onSignal(active = true, nowMs = 21_000L, requiredActiveDurationMs = 20_000L)
        controller.reset()

        val action = controller.onSignal(
            active = true,
            nowMs = 12_000L,
            requiredActiveDurationMs = 0L
        )

        assertEquals(MicrophoneMusicAction.START, action)
    }
}
