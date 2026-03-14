package com.dveamer.babysitter.tutorial

import kotlinx.coroutines.flow.StateFlow

interface TutorialRepository {
    val state: StateFlow<TutorialState>

    suspend fun dismissWelcome()

    suspend fun markSettingsVisited()

    suspend fun finishFirstSettingsVisit()

    suspend fun markSoundEnabled()

    suspend fun markMotionEnabled()

    suspend fun dismissSoundMotionCoach()

    suspend fun markRemoteCoachReady()

    suspend fun dismissRemoteCoach()
}
