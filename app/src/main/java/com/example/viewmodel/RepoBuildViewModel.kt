package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.network.Content
import com.example.network.GenerateContentRequest
import com.example.network.GitHubArtifact
import com.example.network.GitHubWorkflowRun
import com.example.network.Part
import com.example.network.RetrofitClient
import com.example.utils.ApkInstaller
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class DirectApkReleaseInfo(
    val releaseName: String,
    val tagName: String,
    val apkAssetName: String,
    val apkDownloadUrl: String,
    val sizeBytes: Long,
    val publishedAt: String?
)

sealed class BuildStatusState {
    object Idle : BuildStatusState()
    object Triggering : BuildStatusState()
    data class Polling(
        val activeRun: GitHubWorkflowRun?,
        val elapsedTimeStr: String = "",
        val geminiEstimate: String = ""
    ) : BuildStatusState()
    data class Downloading(val progressMessage: String) : BuildStatusState()
    data class Success(val message: String) : BuildStatusState()
    data class Error(val message: String) : BuildStatusState()
}

class RepoBuildViewModel : ViewModel() {

    private val _runs = MutableStateFlow<List<GitHubWorkflowRun>>(emptyList())
    val runs: StateFlow<List<GitHubWorkflowRun>> = _runs.asStateFlow()

    private val _artifactsMap = MutableStateFlow<Map<Long, List<GitHubArtifact>>>(emptyMap())
    val artifactsMap: StateFlow<Map<Long, List<GitHubArtifact>>> = _artifactsMap.asStateFlow()

    private val _latestRepoArtifact = MutableStateFlow<GitHubArtifact?>(null)
    val latestRepoArtifact: StateFlow<GitHubArtifact?> = _latestRepoArtifact.asStateFlow()

    private val _latestDirectApk = MutableStateFlow<DirectApkReleaseInfo?>(null)
    val latestDirectApk: StateFlow<DirectApkReleaseInfo?> = _latestDirectApk.asStateFlow()

    private val _statusState = MutableStateFlow<BuildStatusState>(BuildStatusState.Idle)
    val statusState: StateFlow<BuildStatusState> = _statusState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var pollingJob: Job? = null

    private var localTriggerTimeMs: Long? = null

    fun loadRepoActions(owner: String, repo: String, token: String) {
        val authHeader = if (token.isNotBlank()) (if (token.startsWith("Bearer ")) token else "Bearer $token") else null

        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Fetch workflow runs
                val response = RetrofitClient.githubService.getWorkflowRuns(authHeader, owner, repo)
                _runs.value = response.workflow_runs

                // Fetch run-specific artifacts
                val newArtifactsMap = mutableMapOf<Long, List<GitHubArtifact>>()
                response.workflow_runs.take(5).forEach { run ->
                    try {
                        val artResp = RetrofitClient.githubService.getRunArtifacts(authHeader, owner, repo, run.id)
                        if (artResp.artifacts.isNotEmpty()) {
                            newArtifactsMap[run.id] = artResp.artifacts
                        }
                    } catch (_: Exception) {}
                }
                _artifactsMap.value = newArtifactsMap

                // Fetch all repo-level artifacts to find the latest available ZIP/APK
                try {
                    val allArtResp = RetrofitClient.githubService.getAllRepoArtifacts(authHeader, owner, repo)
                    val latest = allArtResp.artifacts.firstOrNull { !it.expired }
                    _latestRepoArtifact.value = latest
                } catch (_: Exception) {}

                // PRIORITIZE CREATOR'S PRE-BUILT UNZIPPED APK RELEASE ASSETS
                try {
                    val releases = RetrofitClient.githubService.getReleases(authHeader, owner, repo)
                    var foundDirectApk: DirectApkReleaseInfo? = null
                    for (release in releases) {
                        val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                        if (apkAsset != null) {
                            foundDirectApk = DirectApkReleaseInfo(
                                releaseName = release.name ?: release.tag_name,
                                tagName = release.tag_name,
                                apkAssetName = apkAsset.name,
                                apkDownloadUrl = apkAsset.browser_download_url,
                                sizeBytes = apkAsset.size,
                                publishedAt = release.published_at ?: release.created_at
                            )
                            break
                        }
                    }
                    _latestDirectApk.value = foundDirectApk
                } catch (e: Exception) {
                    Log.d("RepoBuildViewModel", "Releases fetch notice: ${e.message}")
                }

                // Check if there is an active building run currently
                val activeRun = response.workflow_runs.firstOrNull { it.status == "in_progress" || it.status == "queued" }
                if (activeRun != null && pollingJob?.isActive != true) {
                    startPollingRuns(owner, repo, token)
                }

            } catch (e: Exception) {
                _statusState.value = BuildStatusState.Error("Failed to load actions: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Smart trigger:
     * 1. Attempts build.yml workflow dispatch
     * 2. If 422/404, checks available workflows and dispatches by ID
     * 3. If dispatch fails or isn't configured, falls back to re-running the most recent build!
     */
    fun triggerBuild(owner: String, repo: String, token: String, ref: String = "main", customGeminiKey: String = "") {
        if (token.isBlank()) {
            _statusState.value = BuildStatusState.Error("Please sign in with a GitHub Personal Access Token to trigger builds.")
            return
        }
        localTriggerTimeMs = System.currentTimeMillis()
        val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"

        viewModelScope.launch {
            _statusState.value = BuildStatusState.Triggering
            try {
                // 1. Try dispatching build.yml
                val resp = RetrofitClient.githubService.triggerBuildWorkflow(
                    token = authHeader,
                    owner = owner,
                    repo = repo,
                    body = com.example.network.WorkflowDispatchBody(ref = ref)
                )

                if (resp.isSuccessful) {
                    _statusState.value = BuildStatusState.Success("Workflow dispatch initiated for build.yml!")
                    startPollingRuns(owner, repo, token, customGeminiKey)
                    return@launch
                }

                // 2. Fallback: Query repo workflows to find active workflow ID
                val workflowsResp = RetrofitClient.githubService.getWorkflows(authHeader, owner, repo)
                val activeWorkflow = workflowsResp.workflows.firstOrNull { it.state == "active" }

                if (activeWorkflow != null) {
                    val dispatchIdResp = RetrofitClient.githubService.dispatchWorkflowById(
                        token = authHeader,
                        owner = owner,
                        repo = repo,
                        workflowId = activeWorkflow.id.toString(),
                        body = com.example.network.WorkflowDispatchBody(ref = ref)
                    )
                    if (dispatchIdResp.isSuccessful) {
                        _statusState.value = BuildStatusState.Success("Triggered workflow '${activeWorkflow.name}'!")
                        startPollingRuns(owner, repo, token, customGeminiKey)
                        return@launch
                    }
                }

                // 3. Fallback: Rerun the most recent workflow run (e.g. Run #3)
                val latestRun = _runs.value.firstOrNull()
                if (latestRun != null) {
                    rerunBuild(owner, repo, token, latestRun.id, customGeminiKey)
                } else {
                    _statusState.value = BuildStatusState.Error("Dispatch failed (${resp.code()}). No existing build run found to rerun.")
                }

            } catch (e: Exception) {
                // Fallback attempt rerun if error occurred
                val latestRun = _runs.value.firstOrNull()
                if (latestRun != null) {
                    rerunBuild(owner, repo, token, latestRun.id, customGeminiKey)
                } else {
                    _statusState.value = BuildStatusState.Error("Error starting build: ${e.message}")
                }
            }
        }
    }

    /**
     * Directly re-runs a specific or most recent workflow run on GitHub Actions.
     */
    fun rerunBuild(owner: String, repo: String, token: String, runId: Long, customGeminiKey: String = "") {
        if (token.isBlank()) {
            _statusState.value = BuildStatusState.Error("Please sign in with a GitHub Personal Access Token to rerun builds.")
            return
        }
        localTriggerTimeMs = System.currentTimeMillis()
        val authHeader = if (token.startsWith("Bearer ")) token else "Bearer $token"

        viewModelScope.launch {
            _statusState.value = BuildStatusState.Triggering
            try {
                val response = RetrofitClient.githubService.rerunWorkflowRun(authHeader, owner, repo, runId)
                if (response.isSuccessful) {
                    _statusState.value = BuildStatusState.Success("Re-running build run #$runId on GitHub!")
                    startPollingRuns(owner, repo, token, customGeminiKey)
                } else {
                    _statusState.value = BuildStatusState.Error("Failed to rerun build (HTTP ${response.code()}).")
                }
            } catch (e: Exception) {
                _statusState.value = BuildStatusState.Error("Error re-running build: ${e.message}")
            }
        }
    }

    fun startPollingRuns(owner: String, repo: String, token: String, customGeminiKey: String = "") {
        pollingJob?.cancel()
        val authHeader = if (token.isNotBlank()) (if (token.startsWith("Bearer ")) token else "Bearer $token") else null
        val geminiKey = customGeminiKey.ifEmpty { BuildConfig.GEMINI_API_KEY }

        pollingJob = viewModelScope.launch {
            var cachedGeminiEstimate = ""
            var lastGeminiQueryTime = 0L

            repeat(36) { iteration -> // Poll every 5s for 3 mins
                try {
                    val response = RetrofitClient.githubService.getWorkflowRuns(authHeader, owner, repo)
                    _runs.value = response.workflow_runs

                    val latestRun = response.workflow_runs.firstOrNull()
                    if (latestRun != null) {
                        val elapsedSeconds = calculateElapsedSeconds(latestRun, localTriggerTimeMs)
                        val elapsedTimeStr = formatDuration(elapsedSeconds)

                        // If building, query Gemini API every 20s for completion estimate
                        if ((latestRun.status == "in_progress" || latestRun.status == "queued") && geminiKey.isNotEmpty()) {
                            val now = System.currentTimeMillis()
                            if (now - lastGeminiQueryTime > 20000 || cachedGeminiEstimate.isEmpty()) {
                                cachedGeminiEstimate = fetchGeminiBuildEstimate(geminiKey, latestRun, elapsedTimeStr)
                                lastGeminiQueryTime = now
                            }
                        }

                        _statusState.value = BuildStatusState.Polling(
                            activeRun = latestRun,
                            elapsedTimeStr = elapsedTimeStr,
                            geminiEstimate = cachedGeminiEstimate
                        )

                        if (latestRun.conclusion == "success") {
                            try {
                                val artResp = RetrofitClient.githubService.getRunArtifacts(authHeader, owner, repo, latestRun.id)
                                val currentMap = _artifactsMap.value.toMutableMap()
                                currentMap[latestRun.id] = artResp.artifacts
                                _artifactsMap.value = currentMap
                                if (artResp.artifacts.isNotEmpty()) {
                                    _latestRepoArtifact.value = artResp.artifacts.first()
                                }
                            } catch (_: Exception) {}

                            _statusState.value = BuildStatusState.Success("Build #${latestRun.run_number} completed successfully! APK is ready.")
                            return@launch
                        } else if (latestRun.conclusion == "failure" || latestRun.conclusion == "cancelled") {
                            _statusState.value = BuildStatusState.Error("Build #${latestRun.run_number} ${latestRun.conclusion}. Check GitHub logs.")
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RepoBuildVM", "Error polling: ${e.message}")
                }
                delay(5000)
            }
        }
    }

    private suspend fun fetchGeminiBuildEstimate(
        apiKey: String,
        run: GitHubWorkflowRun,
        elapsedTimeStr: String
    ): String {
        return try {
            val prompt = "An Android Gradle APK build on GitHub Actions (Run #${run.run_number}, status: ${run.status}) started at ${run.created_at} and has been running for $elapsedTimeStr. Given standard Gradle build times on GitHub runners (typically 2 to 4 minutes), provide a concise 1-sentence estimate of when the APK will be ready and the active build step (e.g., Gradle dependencies, assembleDebug, zipalign)."
            val request = GenerateContentRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = prompt)))
                )
            )
            val resp = RetrofitClient.geminiService.generateContent(apiKey, request)
            resp.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Ready in ~1-2 minutes (Gradle compiling APK)"
        } catch (e: Exception) {
            "Ready in ~1-2 minutes (Gradle compiling APK)"
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
                val artifact = artResp.artifacts.firstOrNull { !it.expired }

                if (artifact == null) {
                    _statusState.value = BuildStatusState.Error("No active APK artifact ZIP found for Run #$runId.")
                    return@launch
                }

                downloadAndInstallArtifact(context, authHeader, artifact)
            } catch (e: Exception) {
                _statusState.value = BuildStatusState.Error("Download/Install failed: ${e.message}")
            }
        }
    }

    fun downloadMostRecentRepoApk(
        context: Context,
        owner: String,
        repo: String,
        token: String
    ) {
        val directApk = _latestDirectApk.value
        if (directApk != null) {
            downloadDirectApkFile(context, directApk.apkDownloadUrl, directApk.apkAssetName, token)
            return
        }

        val authHeader = if (token.isNotBlank()) (if (token.startsWith("Bearer ")) token else "Bearer $token") else null

        viewModelScope.launch {
            _statusState.value = BuildStatusState.Downloading("Finding latest available APK ZIP in repository...")
            try {
                var targetArtifact = _latestRepoArtifact.value

                if (targetArtifact == null) {
                    val allArtResp = RetrofitClient.githubService.getAllRepoArtifacts(authHeader, owner, repo)
                    targetArtifact = allArtResp.artifacts.firstOrNull { !it.expired }
                }

                if (targetArtifact == null) {
                    _statusState.value = BuildStatusState.Error("No available APK artifacts found in $owner/$repo.")
                    return@launch
                }

                downloadAndInstallArtifact(context, authHeader, targetArtifact)
            } catch (e: Exception) {
                _statusState.value = BuildStatusState.Error("Failed to download latest artifact: ${e.message}")
            }
        }
    }

    fun downloadDirectApkFile(
        context: Context,
        downloadUrl: String,
        fileName: String,
        token: String = ""
    ) {
        val authHeader = if (token.isNotBlank()) (if (token.startsWith("Bearer ")) token else "Bearer $token") else null
        viewModelScope.launch {
            _statusState.value = BuildStatusState.Downloading("Downloading creator's pre-built APK '$fileName'...")
            try {
                val responseBody = RetrofitClient.githubService.downloadArtifactZip(authHeader, downloadUrl)
                val result = ApkInstaller.saveAndInstallDirectApk(context, responseBody, fileName)
                if (result.isSuccess) {
                    _statusState.value = BuildStatusState.Success("Pre-built APK downloaded! Launching installer...")
                } else {
                    _statusState.value = BuildStatusState.Error(result.errorMessage ?: "Downloaded file is not a valid APK.")
                }
            } catch (e: Exception) {
                _statusState.value = BuildStatusState.Error("Failed to download direct APK: ${e.message}")
            }
        }
    }

    private suspend fun downloadAndInstallArtifact(
        context: Context,
        authHeader: String?,
        artifact: GitHubArtifact
    ) {
        _statusState.value = BuildStatusState.Downloading("Downloading '${artifact.name}' ZIP (${artifact.size_in_bytes / 1024} KB)...")
        try {
            val responseBody = RetrofitClient.githubService.downloadArtifactZip(
                authHeader,
                artifact.archive_download_url
            )

            _statusState.value = BuildStatusState.Downloading("Extracting .apk package and verifying...")
            val result = ApkInstaller.extractAndInstallApk(context, responseBody)

            if (result.isSuccess) {
                _statusState.value = BuildStatusState.Success("APK extracted & installer launched successfully!")
            } else {
                _statusState.value = BuildStatusState.Error(result.errorMessage ?: "Failed to extract .apk from artifact ZIP file.")
            }
        } catch (e: Exception) {
            _statusState.value = BuildStatusState.Error("Failed to download artifact: ${e.message}")
        }
    }

    private fun calculateElapsedSeconds(run: GitHubWorkflowRun, triggerTimeMs: Long? = null): Long {
        val isoStr = run.run_started_at
            ?: (if (run.status == "in_progress" || run.status == "queued") run.updated_at else null)
            ?: run.created_at

        var secondsFromIso: Long? = null
        if (!isoStr.isNullOrEmpty()) {
            try {
                val cleanStr = isoStr.replace(Regex("\\.\\d+Z$"), "Z")
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(cleanStr)
                if (date != null) {
                    secondsFromIso = ((System.currentTimeMillis() - date.time) / 1000).coerceAtLeast(0)
                }
            } catch (_: Exception) {}
        }

        val secondsFromTrigger = if (triggerTimeMs != null && triggerTimeMs > 0) {
            ((System.currentTimeMillis() - triggerTimeMs) / 1000).coerceAtLeast(0)
        } else null

        if (secondsFromTrigger != null && secondsFromTrigger < 1800) {
            if (secondsFromIso == null || run.run_started_at == null || secondsFromIso > secondsFromTrigger + 120) {
                return secondsFromTrigger
            }
        }

        return secondsFromIso ?: secondsFromTrigger ?: 0
    }

    private fun formatDuration(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    }

    fun formatIsoTimestamp(isoDateStr: String?): String {
        if (isoDateStr.isNullOrEmpty()) return "Unknown"
        return try {
            val cleanStr = isoDateStr.replace(Regex("\\.\\d+Z$"), "Z")
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val date = inputFormat.parse(cleanStr)
            if (date != null) {
                val outputFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm:ss 'UTC'", Locale.US)
                outputFormat.format(date)
            } else isoDateStr
        } catch (_: Exception) {
            isoDateStr
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
