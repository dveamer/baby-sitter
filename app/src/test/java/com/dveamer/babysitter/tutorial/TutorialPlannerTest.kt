package com.dveamer.babysitter.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `shows celebration after remote tutorial finishes and is scheduled`() {
        val step = TutorialPlanner.resolveStep(
            tutorialState = TutorialState(
                welcomeDismissed = true,
                hasVisitedSettings = true,
                firstSettingsVisitFinished = true,
                soundEverEnabled = true,
                motionEverEnabled = true,
                soundMotionCoachDismissed = true,
                remoteCoachReady = true,
                remoteCoachDismissed = true,
                celebrationReady = true
            ),
            isSettingsScreen = false
        )

        assertEquals(TutorialStep.CELEBRATION, step)
    }

    @Test
    fun `uses bright tutorial theme until celebration starts`() {
        assertTrue(TutorialState().useBrightTutorialTheme())
        assertTrue(
            TutorialState(
                welcomeDismissed = true,
                hasVisitedSettings = true,
                firstSettingsVisitFinished = true,
                soundEverEnabled = true,
                motionEverEnabled = true,
                soundMotionCoachDismissed = true,
                remoteCoachReady = true,
                remoteCoachDismissed = true
            ).useBrightTutorialTheme()
        )
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

    @Test
    fun `stops bright tutorial theme once celebration starts`() {
        val useBrightTheme = TutorialState(
            welcomeDismissed = true,
            hasVisitedSettings = true,
            firstSettingsVisitFinished = true,
            soundEverEnabled = true,
            motionEverEnabled = true,
            soundMotionCoachDismissed = true,
            remoteCoachReady = true,
            remoteCoachDismissed = true,
            celebrationReady = true
        ).useBrightTutorialTheme()

        assertFalse(useBrightTheme)
    }
}
