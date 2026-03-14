package com.dveamer.babysitter.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TutorialPlannerTest {

    @Test
    fun `shows welcome first`() {
        val step = TutorialPlanner.resolveStep(
            tutorialState = TutorialState(),
            isSettingsScreen = false
        )

        assertEquals(TutorialStep.WELCOME, step)
    }

    @Test
    fun `shows settings tab after welcome until first settings visit`() {
        val step = TutorialPlanner.resolveStep(
            tutorialState = TutorialState(welcomeDismissed = true),
            isSettingsScreen = false
        )

        assertEquals(TutorialStep.SETTINGS_TAB, step)
    }

    @Test
    fun `shows sound motion coach during first settings visit`() {
        val step = TutorialPlanner.resolveStep(
            tutorialState = TutorialState(
                welcomeDismissed = true,
                hasVisitedSettings = true
            ),
            isSettingsScreen = true
        )

        assertEquals(TutorialStep.SOUND_MOTION, step)
    }

    @Test
    fun `shows remote coach after both monitors were enabled once`() {
        val step = TutorialPlanner.resolveStep(
            tutorialState = TutorialState(
                welcomeDismissed = true,
                hasVisitedSettings = true,
                firstSettingsVisitFinished = true,
                soundEverEnabled = true,
                motionEverEnabled = true,
                soundMotionCoachDismissed = true,
                remoteCoachReady = true
            ),
            isSettingsScreen = true
        )

        assertEquals(TutorialStep.REMOTE_WEB_SERVICE, step)
    }

    @Test
    fun `does not show remote coach before it is marked ready`() {
        val step = TutorialPlanner.resolveStep(
            tutorialState = TutorialState(
                welcomeDismissed = true,
                hasVisitedSettings = true,
                firstSettingsVisitFinished = true,
                soundEverEnabled = true,
                motionEverEnabled = true,
                soundMotionCoachDismissed = true,
                remoteCoachReady = false
            ),
            isSettingsScreen = true
        )

        assertNull(step)
    }

    @Test
    fun `does not show sound motion coach after first settings visit ends`() {
        val step = TutorialPlanner.resolveStep(
            tutorialState = TutorialState(
                welcomeDismissed = true,
                hasVisitedSettings = true,
                firstSettingsVisitFinished = true
            ),
            isSettingsScreen = true
        )

        assertNull(step)
    }
}
