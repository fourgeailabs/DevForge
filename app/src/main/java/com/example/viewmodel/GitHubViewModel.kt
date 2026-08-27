package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.GitHubRepo
import com.example.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GitHubViewModel : ViewModel() {
    private val _repos = MutableStateFlow<List<GitHubRepo>>(emptyList())
    val repos: StateFlow<List<GitHubRepo>> = _repos.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun fetchRepos(pat: String) {
        if (pat.isEmpty()) {
            _error.value = "GitHub PAT not configured in Settings."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Ensure Bearer prefix is used
                val authHeader = if (pat.startsWith("Bearer ")) pat else "Bearer $pat"
                val fetchedRepos = RetrofitClient.githubService.getUserRepos(authHeader)
                _repos.value = fetchedRepos
            } catch (e: Exception) {
                _error.value = "Failed to fetch repos: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
