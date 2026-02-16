package com.dveamer.babysitter.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dveamer.babysitter.settings.SettingsController
import com.dveamer.babysitter.settings.SettingsRepository

class SettingsViewModelFactory(
    private val repository: SettingsRepository,
    private val controller: SettingsController
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(repository, controller) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
