package com.dveamer.babysitter.sleep

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackInactivityControllerTest {

    @Test
    fun `비활성이 grace 이전이면 stop을 요청하지 않는다`() {
        val controller = PlaybackInactivityController(inactivityGraceMs = 15_000L)

        assertEquals(
            PlaybackInactivityAction.NONE,
            controller.onConditionChanged(shouldContinue = false, nowMs = 1_000L)
        )
        assertEquals(
            PlaybackInactivityAction.NONE,
            controller.onConditionChanged(shouldContinue = false, nowMs = 15_999L)
        )
    }

    @Test
    fun `비활성이 grace를 넘기면 한번만 stop을 요청한다`() {
        val controller = PlaybackInactivityController(inactivityGraceMs = 15_000L)

        controller.onConditionChanged(shouldContinue = false, nowMs = 1_000L)

        assertEquals(
            PlaybackInactivityAction.REQUEST_STOP_AFTER_CURRENT_TRACK,
            controller.onConditionChanged(shouldContinue = false, nowMs = 16_000L)
        )
        assertEquals(
            PlaybackInactivityAction.NONE,
            controller.onConditionChanged(shouldContinue = false, nowMs = 17_000L)
        )
    }

    @Test
    fun `stop 요청 후 재활성되면 pending stop을 해제한다`() {
        val controller = PlaybackInactivityController(inactivityGraceMs = 15_000L)

        controller.onConditionChanged(shouldContinue = false, nowMs = 1_000L)
        controller.onConditionChanged(shouldContinue = false, nowMs = 16_000L)

        assertEquals(
            PlaybackInactivityAction.CLEAR_STOP_REQUEST,
            controller.onConditionChanged(shouldContinue = true, nowMs = 17_000L)
        )
        assertEquals(
            PlaybackInactivityAction.NONE,
            controller.onConditionChanged(shouldContinue = true, nowMs = 18_000L)
        )
    }

    @Test
    fun `reset 이후에는 새로운 비활성 구간으로 다시 계산한다`() {
        val controller = PlaybackInactivityController(inactivityGraceMs = 15_000L)

        controller.onConditionChanged(shouldContinue = false, nowMs = 1_000L)
        controller.reset()

        assertEquals(
            PlaybackInactivityAction.NONE,
            controller.onConditionChanged(shouldContinue = false, nowMs = 10_000L)
        )
        assertEquals(
            PlaybackInactivityAction.REQUEST_STOP_AFTER_CURRENT_TRACK,
            controller.onConditionChanged(shouldContinue = false, nowMs = 25_000L)
        )
    }
}
