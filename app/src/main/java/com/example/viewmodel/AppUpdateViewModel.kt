package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.GitHubRelease
import com.example.network.GitHubReleaseAsset
import com.example.network.RetrofitClient
import com.example.settings.SettingsRepository
import com.example.utils.ApkInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

enum class UpdateSourceType {
    GITHUB_RELEASE,
    GITHUB_ACTIONS
}

data class AppUpdateInfo(
    val tagName: String,
    val normalizedVersion: String,
    val title: String,
    val releaseNotes: String,
    val releaseUrl: String,
    val sourceType: UpdateSourceType,
    val apkDownloadUrl: String?,
    val apkAssetUrl: String?,
    val apkFileName: String?,
    val publishedAt: String?,
    val isZipArtifact: Boolean = false
)

sealed class AppUpdateState {
    object Idle : AppUpdateState()
    object Checking : AppUpdateState()
    data class Available(val updateInfo: AppUpdateInfo, val isManualCheck: Boolean) : AppUpdateState()
    data class UpToDate(val currentVersion: String) : AppUpdateState()
    data class DownloadProgress(val message: String) : AppUpdateState()
    data class Error(val message: String) : AppUpdateState()
}

class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val CURRENT_APP_VERSION = "1.19.00"
        const val GITHUB_OWNER = "fourgeailabs"
        const val GITHUB_REPO = "devforge"
    }

    private val settingsRepository = SettingsRepository(application)

    private val _updateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
    val updateState: StateFlow<AppUpdateState> = _updateState.asStateFlow()

    fun checkForUpdates(pat: String = "", forceUserTrigger: Boolean = false) {
        viewModelScope.launch {
            _updateState.value = AppUpdateState.Checking
            try {
                val authHeader = if (pat.isNotBlank()) (if (pat.startsWith("Bearer ")) pat else "Bearer $pat") else null

                // 1. Check GitHub Releases
                var releaseCandidate: AppUpdateInfo? = null
                try {
                    var latestRelease: GitHubRelease? = null
                    try {
                        latestRelease = RetrofitClient.githubService.getLatestRelease(authHeader, GITHUB_OWNER, GITHUB_REPO)
                    } catch (_: Exception) {
                        val releases = RetrofitClient.githubService.getReleases(authHeader, GITHUB_OWNER, GITHUB_REPO, perPage = 5)
                        latestRelease = releases.firstOrNull { !it.draft }
                    }

                    if (latestRelease != null) {
                        val tag = latestRelease.tag_name
                        val normalizedTagVersion = tag.trim().removePrefix("v").removePrefix("V")

                        if (isVersionNewer(latest = normalizedTagVersion, current = CURRENT_APP_VERSION)) {
                            val apkAsset = latestRelease.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                            val releaseUrl = latestRelease.html_url ?: "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/tag/$tag"

                            releaseCandidate = AppUpdateInfo(
                                tagName = tag,
                                normalizedVersion = normalizedTagVersion,
                                title = latestRelease.name ?: "DevForge Pro $tag",
                                releaseNotes = latestRelease.body ?: "New release update available on GitHub.",
                                releaseUrl = releaseUrl,
                                sourceType = UpdateSourceType.GITHUB_RELEASE,
                                apkDownloadUrl = apkAsset?.browser_download_url,
                                apkAssetUrl = apkAsset?.url,
                                apkFileName = apkAsset?.name ?: "DevForgePro-$normalizedTagVersion.apk",
                                publishedAt = latestRelease.published_at ?: latestRelease.created_at,
                                isZipArtifact = false
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AppUpdateViewModel", "Release update check error: ${e.message}")
                }

                // 2. Check GitHub Actions Tab (Workflow Runs & Artifacts)
                var actionsCandidate: AppUpdateInfo? = null
                try {
                    val runsResp = RetrofitClient.githubService.getWorkflowRuns(authHeader, GITHUB_OWNER, GITHUB_REPO, perPage = 10)
                    val successfulRun = runsResp.workflow_runs.firstOrNull { it.status == "completed" && it.conclusion == "success" }

                    if (successfulRun != null) {
                        val runArtifactsResp = RetrofitClient.githubService.getRunArtifacts(authHeader, GITHUB_OWNER, GITHUB_REPO, successfulRun.id)
                        val activeArtifact = runArtifactsResp.artifacts.firstOrNull { !it.expired }

                        if (activeArtifact != null) {
                            val runNumber = successfulRun.run_number
                            val runTag = "Actions-Run-#$runNumber"
                            val commitMsg = successfulRun.head_commit?.message ?: "GitHub Actions Build"
                            val actionsUrl = successfulRun.html_url ?: "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/actions/runs/${successfulRun.id}"

                            actionsCandidate = AppUpdateInfo(
                                tagName = runTag,
                                normalizedVersion = "Run #$runNumber",
                                title = "GitHub Actions Build (Run #$runNumber)",
                                releaseNotes = "Latest build artifact generated by GitHub Actions workflow:\n\"${commitMsg.trim()}\"\nBranch: ${successfulRun.head_branch ?: "main"}",
                                releaseUrl = actionsUrl,
                                sourceType = UpdateSourceType.GITHUB_ACTIONS,
                                apkDownloadUrl = activeArtifact.archive_download_url,
                                apkAssetUrl = null,
                                apkFileName = "DevForgePro-Actions-Run-$runNumber.zip",
                                publishedAt = activeArtifact.created_at ?: successfulRun.created_at,
                                isZipArtifact = true
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AppUpdateViewModel", "Actions update check error: ${e.message}")
                }

                // 3. Select best update candidate
                val bestCandidate = releaseCandidate ?: actionsCandidate

                if (bestCandidate != null) {
                    val skippedVersion = settingsRepository.skippedUpdateVersion.firstOrNull() ?: ""
                    if (!forceUserTrigger && skippedVersion == bestCandidate.tagName) {
                        _updateState.value = AppUpdateState.Idle
                        return@launch
                    }

                    _updateState.value = AppUpdateState.Available(bestCandidate, isManualCheck = forceUserTrigger)
                } else {
                    if (forceUserTrigger) {
                        _updateState.value = AppUpdateState.UpToDate(CURRENT_APP_VERSION)
                    } else {
                        _updateState.value = AppUpdateState.Idle
                    }
                }
            } catch (e: Exception) {
                Log.w("AppUpdateViewModel", "Failed to check for updates: ${e.message}")
                if (forceUserTrigger) {
                    _updateState.value = AppUpdateState.Error("Unable to check GitHub for updates: ${e.localizedMessage ?: e.message}")
                } else {
                    _updateState.value = AppUpdateState.Idle
                }
            }
        }
    }

    fun skipVersion(versionTag: String) {
        viewModelScope.launch {
            settingsRepository.setSkippedUpdateVersion(versionTag)
            _updateState.value = AppUpdateState.Idle
        }
    }

    fun dismissState() {
        _updateState.value = AppUpdateState.Idle
    }

    fun installUpdateNow(
        context: Context,
        updateInfo: AppUpdateInfo,
        pat: String = ""
    ) {
        viewModelScope.launch {
            val downloadUrl = updateInfo.apkDownloadUrl
            val apiAssetUrl = updateInfo.apkAssetUrl
            val fileName = updateInfo.apkFileName ?: "DevForgePro-${updateInfo.normalizedVersion}.apk"

            if (downloadUrl.isNullOrBlank()) {
                _updateState.value = AppUpdateState.Error("Download link not available for update ${updateInfo.tagName}. Visit GitHub to download manually.")
                return@launch
            }

            val authHeader = if (pat.isNotBlank()) (if (pat.startsWith("Bearer ")) pat else "Bearer $pat") else null

            try {
                if (updateInfo.isZipArtifact || updateInfo.sourceType == UpdateSourceType.GITHUB_ACTIONS) {
                    _updateState.value = AppUpdateState.DownloadProgress("Downloading GitHub Actions build artifact ZIP...")
                    val responseBody = RetrofitClient.githubService.downloadArtifactZip(authHeader, downloadUrl)

                    _updateState.value = AppUpdateState.DownloadProgress("Extracting APK package and verifying...")
                    val extractionResult = ApkInstaller.extractAndInstallApk(context, responseBody)

                    if (extractionResult.isSuccess) {
                        _updateState.value = AppUpdateState.Idle
                    } else {
                        _updateState.value = AppUpdateState.Error(
                            extractionResult.errorMessage ?: "Failed to extract valid APK from GitHub Actions artifact ZIP."
                        )
                    }
                } else {
                    _updateState.value = AppUpdateState.DownloadProgress("Downloading update ${updateInfo.tagName}...")
                    val targetUrl = if (!apiAssetUrl.isNullOrBlank()) apiAssetUrl else downloadUrl
                    val responseBody = if (!apiAssetUrl.isNullOrBlank()) {
                        RetrofitClient.githubService.downloadReleaseAsset(
                            token = authHeader,
                            accept = "application/octet-stream",
                            url = targetUrl
                        )
                    } else {
                        RetrofitClient.githubService.downloadArtifactZip(authHeader, targetUrl)
                    }

                    val savedFile = ApkInstaller.saveToPublicDownloadsFolder(context, responseBody, fileName)

                    if (ApkInstaller.isValidApk(context, savedFile)) {
                        _updateState.value = AppUpdateState.DownloadProgress("Launching Android Package Installer...")
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            ApkInstaller.installApk(context, savedFile)
                        }
                        _updateState.value = AppUpdateState.Idle
                    } else {
                        _updateState.value = AppUpdateState.Error("Downloaded file is saved in device Downloads folder ($fileName).")
                    }
                }
            } catch (e: Exception) {
                _updateState.value = AppUpdateState.Error("Failed to download update: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.toIntOrNull() }

        val maxLength = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val lVal = latestParts.getOrElse(i) { 0 }
            val cVal = currentParts.getOrElse(i) { 0 }
            if (lVal > cVal) return true
            if (lVal < cVal) return false
        }
        return false
    }
}

