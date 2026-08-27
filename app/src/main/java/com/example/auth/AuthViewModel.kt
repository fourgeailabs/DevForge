package com.example.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.RetrofitClient
import com.example.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    
    private val _userState = MutableStateFlow<User?>(null)
    val userState: StateFlow<User?> = _userState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()
    
    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    init {
        viewModelScope.launch {
            val savedPat = settingsRepository.githubPat.firstOrNull()
            if (!savedPat.isNullOrEmpty()) {
                authenticateWithGitHub(savedPat, isAutoLogin = true)
            }
        }
    }

    fun authenticateWithGitHub(token: String, isAutoLogin: Boolean = false) {
        if (token.isEmpty()) {
            _authError.value = "Please enter a valid GitHub token."
            return
        }
        
        viewModelScope.launch {
            _isAuthenticating.value = true
            _authError.value = null
            try {
                val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"
                val profile = RetrofitClient.githubService.getUserProfile(authHeader)
                
                settingsRepository.setGithubPat(token)
                
                _userState.value = User(
                    uid = profile.login,
                    name = profile.name ?: profile.login,
                    email = profile.email ?: "",
                    isLoggedIn = true,
                    avatarUrl = profile.avatar_url
                )
            } catch (e: Exception) {
                if (!isAutoLogin) {
                    _authError.value = "Failed to authenticate with GitHub: ${e.message}"
                } else {
                    settingsRepository.setGithubPat("")
                }
            } finally {
                _isAuthenticating.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            settingsRepository.setGithubPat("")
            _userState.value = null
        }
    }
    
    fun clearError() {
        _authError.value = null
    }
}

data class User(
    val uid: String, 
    val name: String, 
    val email: String, 
    val isLoggedIn: Boolean,
    val avatarUrl: String? = null
)
