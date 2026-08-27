package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.GitHubArtifact
import com.example.network.GitHubWorkflowRun
import com.example.network.RetrofitClient
import com.example.utils.ApkInstaller
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class BuildStatusState {
    object Idle : BuildStatusState()
    object Triggering : BuildStatusState()
    data class Polling(val activeRun: GitHubWorkflowRun?) : BuildStatusState()
    data class Downloading(val progressMessage: String) : BuildStatusState()
    data class Success(val message: String) : BuildStatusState()
    data class Error(val message: String) : BuildStatusState()
}

class RepoBuildViewModel : ViewModel() {

    private val _runs = MutableStateFlow<List<GitHubWorkflowRun>>(emptyList())
    val runs: StateFlow<List<GitHubWorkflowRun>> = _runs.asStateFlow()

    private val _artifactsMap = MutableStateFlow<Map<Long, List<GitHubArtifact>>>(emptyMap())
    val artifactsMap: StateFlow<Map<Long, List<GitHubArtifact>>> = _artifactsMap.asStateFlow()

    private val _statusState = MutableStateFlow<BuildStatusState>(BuildStatusState.Idle)
    val statusState: StateFlow<BuildStatusState> = _statusState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var pollingJob: Job? = null

    fun loadRepoActions(owner: String, repo: String, token: String) {
        if (token.isEmpty()) return
        val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = RetrofitClient.githubService.getWorkflowRuns(authHeader, owner, repo)
                _runs.value = response.workflow_runs

                // Fetch artifacts for recent successful runs
                val newArtifactsMap = mutableMapOf<Long, List<GitHubArtifact>>()
                response.workflow_runs.take(5).forEach { run ->
                    if (run.conclusion == "success") {
                        try {
                            val artResp = RetrofitClient.githubService.getRunArtifacts(authHeader, owner, repo, run.id)
                            newArtifactsMap[run.id] = artResp.artifacts
                        } catch (_: Exception) {}
                    }
                }
                _artifactsMap.value = newArtifactsMap
            } catch (e: Exception) {
                _statusState.value = BuildStatusState.Error("Failed to load actions: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun triggerBuild(owner: String, repo: String, token: String, ref: String = "main") {
        if (token.isEmpty()) {
            _statusState.value = BuildStatusState.Error("GitHub token missing.")
            return
        }
        val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"

        viewModelScope.launch {
            _statusState.value = BuildStatusState.Triggering
            try {
                val resp = RetrofitClient.githubService.triggerBuildWorkflow(
                    token = authHeader,
                    owner = owner,
                    repo = repo,
                    body = com.example.network.WorkflowDispatchBody(ref = ref)
                )

                if (resp.isSuccessful) {
                    _statusState.value = BuildStatusState.Success("Workflow dispatch initiated!")
                    startPollingRuns(owner, repo, token)
                } else {
                    _statusState.value = BuildStatusState.Error("Dispatch failed (${resp.code()}). Ensure build.yml exists on branch '$ref'.")
                }
            } catch (e: Exception) {
                _statusState.value = BuildStatusState.Error("Error dispatching build: ${e.message}")
            }
        }
    }

    fun startPollingRuns(owner: String, repo: String, token: String) {
        pollingJob?.cancel()
        val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"

        pollingJob = viewModelScope.launch {
            repeat(30) { // Poll for up to 2.5 minutes (30 * 5s)
                try {
                    val response = RetrofitClient.githubService.getWorkflowRuns(authHeader, owner, repo)
                    _runs.value = response.workflow_runs

                    val latestRun = response.workflow_runs.firstOrNull()
                    if (latestRun != null) {
                        _statusState.value = BuildStatusState.Polling(latestRun)

                        if (latestRun.conclusion == "success") {
                            // Fetch artifact for completed run
                            try {
                                val artResp = RetrofitClient.githubService.getRunArtifacts(authHeader, owner, repo, latestRun.id)
                                val currentMap = _artifactsMap.value.toMutableMap()
                                currentMap[latestRun.id] = artResp.artifacts
                                _artifactsMap.value = currentMap
                            } catch (_: Exception) {}

                            _statusState.value = BuildStatusState.Success("Build completed successfully! APK is ready to install.")
                            return@launch
                        } else if (latestRun.conclusion == "failure" || latestRun.conclusion == "cancelled") {
                            _statusState.value = BuildStatusState.Error("Build ${latestRun.conclusion}: Check GitHub logs.")
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    // Ignore transient errors while polling
                }
                delay(5000)
            }
        }
    }

    fun downloadAndInstallApk(
        context: Context,
        owner: String,
        repo: String,
        runId: Long,
        token: String
    ) {
        if (token.isEmpty()) return
        val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"

        viewModelScope.launch {
            _statusState.value = BuildStatusState.Downloading("Fetching APK artifact download link...")
            try {
                val artResp = RetrofitClient.githubService.getRunArtifacts(authHeader, owner, repo, runId)
                val artifact = artResp.artifacts.firstOrNull()

                if (artifact == null) {
                    _statusState.value = BuildStatusState.Error("No APK artifact found for this build run.")
                    return@launch
                }

                _statusState.value = BuildStatusState.Downloading("Downloading APK artifact (${artifact.size_in_bytes / 1024} KB)...")
                val responseBody = RetrofitClient.githubService.downloadArtifactZip(
                    authHeader,
                    artifact.archive_download_url
                )

                _statusState.value = BuildStatusState.Downloading("Extracting and launching installer...")
                val apkFile = ApkInstaller.extractAndInstallApk(context, responseBody)

                if (apkFile != null) {
                    _statusState.value = BuildStatusState.Success("APK unzipped & installer launched!")
                } else {
                    _statusState.value = BuildStatusState.Error("Failed to extract APK from ZIP file.")
                }
            } catch (e: Exception) {
                _statusState.value = BuildStatusState.Error("Download/Install failed: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
