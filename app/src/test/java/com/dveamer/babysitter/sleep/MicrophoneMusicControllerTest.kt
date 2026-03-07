package com.dveamer.babysitter.sleep

import org.junit.Assert.assertEquals
import org.junit.Test

class MicrophoneMusicControllerTest {

    @Test
    fun `inactive에서 active로 바뀌면 lullaby start`() {
        val controller = MicrophoneMusicController()

        val action = controller.onSignal(active = true)

        assertEquals(MicrophoneMusicAction.START, action)
    }

    @Test
    fun `같은 active 구간에서는 start를 반복하지 않는다`() {
        val controller = MicrophoneMusicController()

        assertEquals(MicrophoneMusicAction.START, controller.onSignal(active = true))
        assertEquals(MicrophoneMusicAction.NONE, controller.onSignal(active = true))
        assertEquals(MicrophoneMusicAction.NONE, controller.onSignal(active = true))
    }

    @Test
    fun `억제 구간에서 inactive가 들어오면 다음 active에서 다시 start한다`() {
        val controller = MicrophoneMusicController()

        assertEquals(MicrophoneMusicAction.START, controller.onSignal(active = true))
        assertEquals(MicrophoneMusicAction.NONE, controller.onSignal(active = false))

        val action = controller.onSignal(active = true)

        assertEquals(MicrophoneMusicAction.START, action)
    }

    @Test
    fun `inactive가 유지되면 start하지 않는다`() {
        val controller = MicrophoneMusicController()

        controller.onSignal(active = true)
        controller.onSignal(active = false)
        val action = controller.onSignal(active = false)

        assertEquals(MicrophoneMusicAction.NONE, action)
    }

    @Test
    fun `reset 이후에는 다시 start 가능`() {
        val controller = MicrophoneMusicController()

        controller.onSignal(active = true)
        controller.reset()

        val action = controller.onSignal(active = true)

        assertEquals(MicrophoneMusicAction.START, action)
    }
}
