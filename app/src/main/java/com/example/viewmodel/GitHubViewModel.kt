package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.GitHubRepo
import com.example.network.GitHubWorkflowRun
import com.example.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GitHubViewModel : ViewModel() {
    private val _repos = MutableStateFlow<List<GitHubRepo>>(emptyList())
    val repos: StateFlow<List<GitHubRepo>> = _repos.asStateFlow()

    private val _activeWorkflowRuns = MutableStateFlow<Map<String, GitHubWorkflowRun>>(emptyMap())
    val activeWorkflowRuns: StateFlow<Map<String, GitHubWorkflowRun>> = _activeWorkflowRuns.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _activeOwnerFilter = MutableStateFlow<String?>(null)
    val activeOwnerFilter: StateFlow<String?> = _activeOwnerFilter.asStateFlow()

    fun fetchRepos(pat: String) {
        if (pat.isEmpty()) {
            _error.value = "GitHub PAT not configured."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _activeOwnerFilter.value = null
            try {
                // Ensure Bearer prefix is used
                val authHeader = if (pat.startsWith("Bearer ")) pat else "Bearer $pat"
                val fetchedRepos = RetrofitClient.githubService.getUserRepos(authHeader)
                _repos.value = fetchedRepos
                checkActiveWorkflowRuns(pat)
            } catch (e: Exception) {
                _error.value = "Failed to fetch repos: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Resolves a public GitHub repository link (e.g. https://github.com/fourgeailabs/DevForge or owner/repo)
     * or a GitHub creator page (e.g. https://github.com/fourgeailabs or fourgeailabs).
     */
    fun fetchPublicRepoOrUser(urlOrHandle: String, pat: String = "") {
        val cleanInput = urlOrHandle.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("github.com/")
            .removeSuffix(".git")
            .trim('/')

        if (cleanInput.isEmpty()) {
            _error.value = "Please enter a valid GitHub repository or creator URL."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val authHeader = if (pat.isNotBlank()) (if (pat.startsWith("Bearer ")) pat else "Bearer $pat") else null
                val parts = cleanInput.split("/")

                if (parts.size >= 2) {
                    // Direct owner/repo link
                    val owner = parts[0]
                    val repo = parts[1]
                    _activeOwnerFilter.value = "$owner/$repo"
                    val singleRepo = RetrofitClient.githubService.getSingleRepo(authHeader, owner, repo)
                    _repos.value = listOf(singleRepo)
                } else {
                    // Creator / Username / Org
                    val username = parts[0]
                    _activeOwnerFilter.value = username
                    try {
                        val userRepos = RetrofitClient.githubService.getPublicUserRepos(authHeader, username)
                        if (userRepos.isNotEmpty()) {
                            _repos.value = userRepos
                        } else {
                            val orgRepos = RetrofitClient.githubService.getPublicOrgRepos(authHeader, username)
                            _repos.value = orgRepos
                        }
                    } catch (_: Exception) {
                        val orgRepos = RetrofitClient.githubService.getPublicOrgRepos(authHeader, username)
                        _repos.value = orgRepos
                    }
                }
                checkActiveWorkflowRuns(pat)
            } catch (e: Exception) {
                val errMessage = if (e is retrofit2.HttpException && e.code() == 404) {
                    "GitHub repository or creator '$cleanInput' not found (HTTP 404). Check the repository name or enter your GitHub PAT in Settings for private repositories."
                } else {
                    "Unable to load public GitHub repository or creator: ${e.localizedMessage ?: e.message}"
                }
                _error.value = errMessage
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun checkActiveWorkflowRuns(pat: String = "") {
        val currentRepos = _repos.value
        if (currentRepos.isEmpty()) return

        viewModelScope.launch {
            val authHeader = if (pat.isNotBlank()) (if (pat.startsWith("Bearer ")) pat else "Bearer $pat") else null
            val updatedMap = _activeWorkflowRuns.value.toMutableMap()

            currentRepos.forEach { repo ->
                val parts = repo.full_name.split("/")
                if (parts.size == 2) {
                    try {
                        val runsResp = RetrofitClient.githubService.getWorkflowRuns(
                            token = authHeader,
                            owner = parts[0],
                            repo = parts[1],
                            perPage = 5
                        )
                        val activeRun = runsResp.workflow_runs.firstOrNull { run ->
                            run.status == "in_progress" || run.status == "queued" || run.status == "waiting" || run.status == "pending" || run.status == "requested"
                        }
                        if (activeRun != null) {
                            updatedMap[repo.full_name] = activeRun
                        } else {
                            updatedMap.remove(repo.full_name)
                        }
                    } catch (_: Exception) {
                        // Ignore transient network errors
                    }
                }
            }
            _activeWorkflowRuns.value = updatedMap
        }
    }

    fun clearOwnerFilter(pat: String) {
        _activeOwnerFilter.value = null
        if (pat.isNotEmpty()) {
            fetchRepos(pat)
        } else {
            _repos.value = emptyList()
        }
    }
}

