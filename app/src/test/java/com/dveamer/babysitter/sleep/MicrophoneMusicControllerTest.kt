package com.dveamer.babysitter.sleep

import org.junit.Assert.assertEquals
import org.junit.Test

class MicrophoneMusicControllerTest {

    @Test
    fun `inactive에서 active로 바뀌면 lullaby start`() {
        val controller = MicrophoneMusicController()

        val action = controller.onSignal(
            active = true,
            lullabyActive = false
        )

        assertEquals(MicrophoneMusicAction.START, action)
    }

    @Test
    fun `같은 active 구간에서는 start를 반복하지 않는다`() {
        val controller = MicrophoneMusicController()

        assertEquals(MicrophoneMusicAction.START, controller.onSignal(active = true, lullabyActive = false))
        assertEquals(MicrophoneMusicAction.NONE, controller.onSignal(active = true, lullabyActive = false))
        assertEquals(MicrophoneMusicAction.NONE, controller.onSignal(active = true, lullabyActive = true))
    }

    @Test
    fun `마이크가 시작한 lullaby는 inactive가 되면 stop`() {
        val controller = MicrophoneMusicController()

        controller.onSignal(active = true, lullabyActive = false)
        val action = controller.onSignal(
            active = false,
            lullabyActive = true
        )

        assertEquals(MicrophoneMusicAction.STOP, action)
    }

    @Test
    fun `마이크가 시작하지 않은 lullaby는 inactive여도 stop하지 않는다`() {
        val controller = MicrophoneMusicController()

        val action = controller.onSignal(
            active = false,
            lullabyActive = true
        )

        assertEquals(MicrophoneMusicAction.NONE, action)
    }

    @Test
    fun `lullaby와 sound가 모두 꺼지면 다음 active에서 다시 start 가능`() {
        val controller = MicrophoneMusicController()

        controller.onSignal(active = true, lullabyActive = false)
        controller.onSignal(active = false, lullabyActive = true)
        controller.onSignal(active = false, lullabyActive = false)

        val action = controller.onSignal(
            active = true,
            lullabyActive = false
        )

        assertEquals(MicrophoneMusicAction.START, action)
    }
}
