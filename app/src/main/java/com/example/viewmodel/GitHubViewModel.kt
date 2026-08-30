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

enum class SearchTargetMode(val prefix: String, val label: String) {
    DEV("Dev:", "Developer"),
    REPO("Repo:", "Repository")
}

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

    private fun sortReposByMostRecentlyModified(list: List<GitHubRepo>): List<GitHubRepo> {
        return list.sortedWith(
            compareByDescending<GitHubRepo> { repo ->
                repo.pushed_at ?: repo.updated_at ?: ""
            }.thenByDescending { repo ->
                repo.id
            }
        )
    }

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
                _repos.value = sortReposByMostRecentlyModified(fetchedRepos)
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
                            _repos.value = sortReposByMostRecentlyModified(userRepos)
                        } else {
                            val orgRepos = RetrofitClient.githubService.getPublicOrgRepos(authHeader, username)
                            _repos.value = sortReposByMostRecentlyModified(orgRepos)
                        }
                    } catch (_: Exception) {
                        val orgRepos = RetrofitClient.githubService.getPublicOrgRepos(authHeader, username)
                        _repos.value = sortReposByMostRecentlyModified(orgRepos)
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

    /**
     * Performs a GitHub API search specifically targetting either Developers/Creators or Repository Names.
     */
    fun performGitHubSearch(mode: SearchTargetMode, searchQuery: String, pat: String = "") {
        val cleanQuery = searchQuery.trim()
            .removePrefix("Dev:")
            .removePrefix("dev:")
            .removePrefix("Repo:")
            .removePrefix("repo:")
            .trim()

        if (cleanQuery.isEmpty()) {
            if (pat.isNotEmpty() && _activeOwnerFilter.value != null) {
                clearOwnerFilter(pat)
            }
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val authHeader = if (pat.isNotBlank()) (if (pat.startsWith("Bearer ")) pat else "Bearer $pat") else null

            try {
                if (mode == SearchTargetMode.DEV) {
                    _activeOwnerFilter.value = "Dev: $cleanQuery"
                    val cleanUsername = cleanQuery.replace(" ", "").lowercase()

                    var resultList: List<GitHubRepo> = emptyList()

                    // First attempt: direct user or org repos
                    try {
                        val userRepos = RetrofitClient.githubService.getPublicUserRepos(authHeader, cleanUsername)
                        if (userRepos.isNotEmpty()) {
                            resultList = userRepos
                        } else {
                            val orgRepos = RetrofitClient.githubService.getPublicOrgRepos(authHeader, cleanUsername)
                            resultList = orgRepos
                        }
                    } catch (_: Exception) {
                        try {
                            val orgRepos = RetrofitClient.githubService.getPublicOrgRepos(authHeader, cleanUsername)
                            resultList = orgRepos
                        } catch (_: Exception) {
                            // Fallback to GitHub Search API for user
                            val searchResp = RetrofitClient.githubService.searchRepositories(authHeader, "user:$cleanUsername")
                            resultList = searchResp.items
                        }
                    }

                    if (resultList.isEmpty()) {
                        // General query fallback
                        val fallbackSearch = RetrofitClient.githubService.searchRepositories(authHeader, cleanQuery)
                        resultList = fallbackSearch.items
                    }

                    if (resultList.isEmpty()) {
                        _error.value = "No public GitHub repositories found for developer '$cleanQuery'."
                    }

                    _repos.value = sortReposByMostRecentlyModified(resultList)
                } else {
                    // Search by Repository Name
                    _activeOwnerFilter.value = "Repo: $cleanQuery"
                    val searchResp = RetrofitClient.githubService.searchRepositories(authHeader, cleanQuery)
                    if (searchResp.items.isEmpty()) {
                        _error.value = "No public GitHub repositories found matching repository '$cleanQuery'."
                    }
                    _repos.value = sortReposByMostRecentlyModified(searchResp.items)
                }
                checkActiveWorkflowRuns(pat)
            } catch (e: Exception) {
                _error.value = "GitHub search failed for '$cleanQuery': ${e.localizedMessage ?: e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

