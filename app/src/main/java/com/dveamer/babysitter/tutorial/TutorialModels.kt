package com.dveamer.babysitter.tutorial

data class TutorialState(
    val welcomeDismissed: Boolean = false,
    val hasVisitedSettings: Boolean = false,
    val firstSettingsVisitFinished: Boolean = false,
    val soundEverEnabled: Boolean = false,
    val motionEverEnabled: Boolean = false,
    val soundMotionCoachDismissed: Boolean = false,
    val remoteCoachReady: Boolean = false,
    val remoteCoachDismissed: Boolean = false,
    val celebrationReady: Boolean = false,
    val celebrationDismissed: Boolean = false
)

enum class TutorialStep {
    WELCOME,
    SETTINGS_TAB,
    SOUND_MOTION,
    REMOTE_WEB_SERVICE,
    CELEBRATION
}

object TutorialPlanner {
    fun resolveStep(
        tutorialState: TutorialState,
        isSettingsScreen: Boolean
    ): TutorialStep? {
        return when {
            !tutorialState.welcomeDismissed -> TutorialStep.WELCOME
            !tutorialState.hasVisitedSettings && !isSettingsScreen -> TutorialStep.SETTINGS_TAB
            tutorialState.hasVisitedSettings &&
                !tutorialState.firstSettingsVisitFinished &&
                !tutorialState.soundMotionCoachDismissed &&
                isSettingsScreen -> TutorialStep.SOUND_MOTION

            tutorialState.remoteCoachReady &&
                !tutorialState.remoteCoachDismissed &&
                isSettingsScreen -> TutorialStep.REMOTE_WEB_SERVICE

            tutorialState.celebrationReady &&
                !tutorialState.celebrationDismissed -> TutorialStep.CELEBRATION

            else -> null
        }
    }
}

fun TutorialState.useBrightTutorialTheme(): Boolean {
    return !celebrationReady && !celebrationDismissed
}
