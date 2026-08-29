package com.example.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)

    val isDarkMode: StateFlow<Boolean> = repository.isDarkMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val selectedAiProvider: StateFlow<String> = repository.selectedAiProvider.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Google Gemini"
    )

    val geminiApiKey: StateFlow<String> = repository.geminiApiKey.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val aiApiKey: StateFlow<String> = repository.geminiApiKey.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val githubPat: StateFlow<String> = repository.githubPat.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDarkMode(enabled)
        }
    }

    fun setSelectedAiProvider(provider: String) {
        viewModelScope.launch {
            repository.setSelectedAiProvider(provider)
        }
    }

    fun setAiApiKey(key: String, provider: String = selectedAiProvider.value) {
        viewModelScope.launch {
            repository.setAiApiKey(provider, key)
        }
    }

    fun setGeminiApiKey(key: String) {
        setAiApiKey(key, "Google Gemini")
    }

    fun setGithubPat(token: String) {
        viewModelScope.launch {
            repository.setGithubPat(token)
        }
    }
}
