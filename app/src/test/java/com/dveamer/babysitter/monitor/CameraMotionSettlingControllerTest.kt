package com.dveamer.babysitter.monitor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraMotionSettlingControllerTest {

    @Test
    fun `30퍼센트 이상 장면 변화는 즉시 안정화하고 awake 누적을 초기화한다`() {
        val controller = CameraMotionSettlingController()

        val decision = controller.onObservation(
            observation = motion(changedRatio = 0.31, activeTileCount = 4),
            nowMs = 1_000L
        )

        assertFalse(decision.active)
        assertTrue(decision.resetAwakeAccumulation)
        assertTrue(decision.settling)
    }

    @Test
    fun `넓은 장면 변화가 최근 세 번 중 두 번이면 안정화한다`() {
        val controller = CameraMotionSettlingController()

        val first = controller.onObservation(
            observation = motion(changedRatio = 0.16, activeTileCount = 6),
            nowMs = 1_000L
        )
        controller.onObservation(
            observation = motion(changedRatio = 0.08, activeTileCount = 2),
            nowMs = 2_000L
        )
        val third = controller.onObservation(
            observation = motion(changedRatio = 0.18, activeTileCount = 7),
            nowMs = 3_000L
        )

        assertTrue(first.active)
        assertFalse(first.settling)
        assertFalse(third.active)
        assertTrue(third.resetAwakeAccumulation)
        assertTrue(third.settling)
    }

    @Test
    fun `변화가 좁은 영역에 머물면 큰 움직임으로 보지 않는다`() {
        val controller = CameraMotionSettlingController()

        repeat(3) { index ->
            val decision = controller.onObservation(
                observation = motion(changedRatio = 0.20, activeTileCount = 3),
                nowMs = index * 1_000L
            )
            assertTrue(decision.active)
            assertFalse(decision.settling)
        }
    }

    @Test
    fun `큰 움직임 뒤 10초간 조용하면 안정화를 종료한다`() {
        val controller = CameraMotionSettlingController()
        controller.onObservation(
            observation = motion(changedRatio = 0.35, activeTileCount = 9),
            nowMs = 0L
        )

        val quietStarted = controller.onObservation(still(), nowMs = 1_000L)
        val almostSettled = controller.onObservation(still(), nowMs = 10_999L)
        val settled = controller.onObservation(still(), nowMs = 11_000L)

        assertTrue(quietStarted.settling)
        assertTrue(almostSettled.settling)
        assertFalse(settled.settling)
        assertFalse(settled.active)
    }

    @Test
    fun `30초 상한 뒤에는 큰 움직임도 다시 일반 감지로 전달한다`() {
        val controller = CameraMotionSettlingController()
        val largeMotion = motion(changedRatio = 0.35, activeTileCount = 9)
        controller.onObservation(largeMotion, nowMs = 0L)

        val maxReached = controller.onObservation(largeMotion, nowMs = 30_000L)
        val cooldown = controller.onObservation(largeMotion, nowMs = 31_000L)

        assertTrue(maxReached.active)
        assertFalse(maxReached.settling)
        assertTrue(cooldown.active)
        assertFalse(cooldown.settling)
    }

    private fun motion(
        changedRatio: Double,
        activeTileCount: Int
    ): CameraMotionObservation {
        return CameraMotionObservation(
            active = true,
            changedRatio = changedRatio,
            activeTileCount = activeTileCount
        )
    }

    private fun still(): CameraMotionObservation {
        return CameraMotionObservation(
            active = false,
            changedRatio = 0.0,
            activeTileCount = 0
        )
    }
}
