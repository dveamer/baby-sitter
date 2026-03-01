package com.dveamer.babysitter.sleep

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class SleepRuntimeStatus(
    val monitoringActive: Boolean = false,
    val lullabyActive: Boolean = false
)

object SleepRuntimeStatusStore {
    private val mutableState = MutableStateFlow(SleepRuntimeStatus())
    val state: StateFlow<SleepRuntimeStatus> = mutableState

    fun setMonitoringActive(active: Boolean) {
        mutableState.value = mutableState.value.copy(monitoringActive = active)
    }

    fun setLullabyActive(active: Boolean) {
        mutableState.value = mutableState.value.copy(lullabyActive = active)
    }

    fun reset() {
        mutableState.value = SleepRuntimeStatus()
    }
}
