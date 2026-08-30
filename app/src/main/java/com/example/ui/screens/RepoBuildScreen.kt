package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.network.GitHubArtifact
import com.example.network.GitHubWorkflowRun
import com.example.settings.SettingsViewModel
import com.example.viewmodel.BuildStatusState
import com.example.viewmodel.MultiPlatformReleaseAsset
import com.example.viewmodel.RepoBuildViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepoBuildScreen(
    owner: String,
    repo: String,
    onNavigateBack: () -> Unit,
    onNavigateToEditor: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(),
    buildViewModel: RepoBuildViewModel = viewModel()
) {
    val context = LocalContext.current
    val githubPat by settingsViewModel.githubPat.collectAsState()
    val aiApiKey by settingsViewModel.aiApiKey.collectAsState()
    val runs by buildViewModel.runs.collectAsState()
    val artifactsMap by buildViewModel.artifactsMap.collectAsState()
    val latestRepoArtifact by buildViewModel.latestRepoArtifact.collectAsState()
    val latestDirectApk by buildViewModel.latestDirectApk.collectAsState()
    val multiPlatformReleaseAssets by buildViewModel.multiPlatformReleaseAssets.collectAsState()
    val statusState by buildViewModel.statusState.collectAsState()
    val isLoading by buildViewModel.isLoading.collectAsState()

    val latestRun = runs.firstOrNull()

    LaunchedEffect(owner, repo, githubPat) {
        buildViewModel.loadRepoActions(owner, repo, githubPat)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = repo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "$owner/$repo • Actions & Builds",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToEditor) {
                        Icon(
                            Icons.Default.Code,
                            contentDescription = "Code Editor",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = {
                if (githubPat.isNotEmpty()) {
                    buildViewModel.loadRepoActions(owner, repo, githubPat)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = 32.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
            // PRIORITIZED: Creator's Direct Pre-Built APK Banner
            item {
                if (latestDirectApk != null) {
                    val directApk = latestDirectApk!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            "PRIORITIZED CREATOR APK",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = directApk.apkAssetName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${directApk.releaseName} (${directApk.tagName}) • ${directApk.sizeBytes / 1024} KB",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    buildViewModel.downloadDirectApkFile(
                                        context = context,
                                        downloadUrl = directApk.apkDownloadUrl,
                                        apiAssetUrl = directApk.apiAssetUrl,
                                        fileName = directApk.apkAssetName,
                                        token = githubPat
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(Icons.Default.GetApp, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download Creator's Pre-Built APK", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Multi-Platform Release Downloads Card (Windows, Mac, Linux, iOS, Android)
            if (multiPlatformReleaseAssets.isNotEmpty()) {
                item {
                    MultiPlatformDownloadsCard(
                        assets = multiPlatformReleaseAssets,
                        onDownloadAsset = { asset ->
                            buildViewModel.downloadFileToPublicDownloads(
                                context = context,
                                downloadUrl = asset.browserDownloadUrl,
                                apiAssetUrl = asset.apiAssetUrl,
                                fileName = asset.fileName,
                                token = githubPat
                            )
                        }
                    )
                }
            }

            // Most Recent Build & Trigger Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Build,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GitHub Actions APK Builder",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Builds & deploys Android APKs automatically",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }

                        if (latestRun != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "MOST RECENT BUILD AVAILABLE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Run #${latestRun.run_number}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.weight(1f))

                                val statusColor = when (latestRun.conclusion) {
                                    "success" -> Color(0xFF2E7D32)
                                    "failure" -> MaterialTheme.colorScheme.error
                                    "cancelled" -> Color.Gray
                                    else -> Color(0xFF0288D1)
                                }

                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = (latestRun.conclusion ?: latestRun.status).uppercase(),
                                        color = statusColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = buildViewModel.formatIsoTimestamp(latestRun.run_started_at ?: latestRun.updated_at ?: latestRun.created_at),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (latestRun.head_commit?.message != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Commit: ${latestRun.head_commit.message}",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Trigger New Build Button
                        Button(
                            onClick = {
                                buildViewModel.triggerBuild(owner, repo, githubPat, customGeminiKey = aiApiKey)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            enabled = statusState !is BuildStatusState.Triggering && statusState !is BuildStatusState.Downloading
                        ) {
                            if (statusState is BuildStatusState.Triggering) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Starting Build...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Create APK (Trigger Build)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }

                        if (latestRun != null) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Rerun Latest Build Button
                            OutlinedButton(
                                onClick = {
                                    buildViewModel.rerunBuild(owner, repo, githubPat, latestRun.id, customGeminiKey = aiApiKey)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Rerun Most Recent Build (#${latestRun.run_number})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // Download Most Recent APK Zip Banner
            item {
                if (latestRepoArtifact != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF1B5E20)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.FolderZip,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Most Recent APK Zip Available",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "'${latestRepoArtifact?.name}' • ${((latestRepoArtifact?.size_in_bytes ?: 0) / 1024)} KB",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    buildViewModel.downloadMostRecentRepoApk(context, owner, repo, githubPat)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF1B5E20)
                                )
                            ) {
                                Icon(Icons.Default.GetApp, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Download Most Recent APK Zip & Install", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Live Build Status & Gemini ETA Monitor Banner
            item {
                AnimatedVisibility(visible = statusState !is BuildStatusState.Idle) {
                    val state = statusState
                    val (bgColor, textColor, icon) = when (state) {
                        is BuildStatusState.Polling -> Triple(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.onPrimaryContainer,
                            Icons.Default.HourglassTop
                        )
                        is BuildStatusState.Downloading -> Triple(
                            MaterialTheme.colorScheme.tertiaryContainer,
                            MaterialTheme.colorScheme.onTertiaryContainer,
                            Icons.Default.CloudDownload
                        )
                        is BuildStatusState.Success -> Triple(
                            Color(0xFF2E7D32),
                            Color.White,
                            Icons.Default.CheckCircle
                        )
                        is BuildStatusState.Error -> Triple(
                            MaterialTheme.colorScheme.errorContainer,
                            MaterialTheme.colorScheme.onErrorContainer,
                            Icons.Default.Error
                        )
                        else -> Triple(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            Icons.Default.Info
                        )
                    }

                    Surface(
                        color = bgColor,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    val titleMsg = when (state) {
                                        is BuildStatusState.Polling -> "Build Run #${state.activeRun?.run_number ?: ""} In Progress"
                                        is BuildStatusState.Downloading -> "Downloading Artifact..."
                                        is BuildStatusState.Success -> "Action Completed"
                                        is BuildStatusState.Error -> "Build Notice"
                                        else -> ""
                                    }
                                    Text(
                                        text = titleMsg,
                                        color = textColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    if (state is BuildStatusState.Polling && state.elapsedTimeStr.isNotEmpty()) {
                                        Text(
                                            text = "Running for: ${state.elapsedTimeStr}",
                                            color = textColor.copy(alpha = 0.9f),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            val descMsg = when (state) {
                                is BuildStatusState.Downloading -> state.progressMessage
                                is BuildStatusState.Success -> state.message
                                is BuildStatusState.Error -> state.message
                                else -> null
                            }

                            if (descMsg != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = descMsg,
                                    color = textColor,
                                    fontSize = 12.sp
                                )
                            }

                            if (state is BuildStatusState.Polling && state.geminiEstimate.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = textColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = textColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Cloud AI Estimate: ${state.geminiEstimate}",
                                            color = textColor,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            if (state is BuildStatusState.Polling || state is BuildStatusState.Downloading) {
                                Spacer(modifier = Modifier.height(12.dp))
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }

            // Section Header
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Workflow Runs History",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = { buildViewModel.loadRepoActions(owner, repo, githubPat) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Runs")
                    }
                }
            }

            if (isLoading && runs.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (runs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No workflow runs found yet",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Tap 'Create APK' above to trigger your first build on GitHub Actions.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(runs) { run ->
                    WorkflowRunCard(
                        run = run,
                        artifacts = artifactsMap[run.id] ?: emptyList(),
                        formatIsoTimestamp = { buildViewModel.formatIsoTimestamp(it) },
                        onDownloadAndInstall = {
                            buildViewModel.downloadAndInstallApk(context, owner, repo, run.id, githubPat)
                        },
                        onRerunRun = {
                            buildViewModel.rerunBuild(owner, repo, githubPat, run.id, customGeminiKey = aiApiKey)
                        }
                    )
                }
            }
        }
    }
}
}

@Composable
fun WorkflowRunCard(
    run: GitHubWorkflowRun,
    artifacts: List<GitHubArtifact>,
    formatIsoTimestamp: (String?) -> String,
    onDownloadAndInstall: () -> Unit,
    onRerunRun: () -> Unit
) {
    val statusColor = when (run.conclusion) {
        "success" -> Color(0xFF2E7D32)
        "failure" -> MaterialTheme.colorScheme.error
        "cancelled" -> Color.Gray
        else -> if (run.status == "in_progress") Color(0xFF0288D1) else Color(0xFFED6C02)
    }

    val statusIcon = when (run.conclusion) {
        "success" -> Icons.Default.CheckCircle
        "failure" -> Icons.Default.Cancel
        else -> if (run.status == "in_progress") Icons.Default.Sync else Icons.Default.Schedule
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Run #${run.run_number}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = (run.conclusion ?: run.status).uppercase(),
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (run.head_commit?.message != null) {
                Text(
                    text = run.head_commit.message,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Timestamp: ${formatIsoTimestamp(run.created_at)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Branch: ${run.head_branch ?: "main"}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRerunRun,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rerun Run", fontSize = 12.sp)
                }

                if (run.conclusion == "success" || artifacts.isNotEmpty()) {
                    Button(
                        onClick = onDownloadAndInstall,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.GetApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun MultiPlatformDownloadsCard(
    assets: List<MultiPlatformReleaseAsset>,
    onDownloadAsset: (MultiPlatformReleaseAsset) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Release Downloads (All Platforms)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Windows • Mac • Linux • iOS • Android • Archives",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                assets.take(15).forEach { asset ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = asset.platform.iconBadge,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(end = 10.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = asset.platform.displayName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = asset.tagName,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = asset.fileName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${asset.sizeBytes / 1024} KB",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { onDownloadAsset(asset) },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "Download to device Downloads folder",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
