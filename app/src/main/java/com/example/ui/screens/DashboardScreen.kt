package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.example.network.GitHubRepo
import com.example.network.GitHubWorkflowRun
import com.example.settings.SettingsViewModel
import com.example.ui.components.AppUpdateDialog
import com.example.viewmodel.AppUpdateViewModel
import com.example.viewmodel.GitHubViewModel
import com.example.viewmodel.SearchTargetMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToRepoBuild: (String, String) -> Unit,
    onNavigateToSettings: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel(),
    gitHubViewModel: GitHubViewModel = viewModel(),
    appUpdateViewModel: AppUpdateViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val githubPat by settingsViewModel.githubPat.collectAsState()
    val updateState by appUpdateViewModel.updateState.collectAsState()
    val repos by gitHubViewModel.repos.collectAsState()
    val activeRuns by gitHubViewModel.activeWorkflowRuns.collectAsState()
    val isLoading by gitHubViewModel.isLoading.collectAsState()
    val error by gitHubViewModel.error.collectAsState()
    val activeOwnerFilter by gitHubViewModel.activeOwnerFilter.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var searchTargetMode by remember { mutableStateOf(SearchTargetMode.REPO) }
    var showPublicLinkDialog by remember { mutableStateOf(false) }
    var publicUrlInput by remember { mutableStateOf("") }

    var currentTimeMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        appUpdateViewModel.checkForUpdates(githubPat, forceUserTrigger = false)
    }

    fun extractRawQueryAndMode(input: String, currentMode: SearchTargetMode): Pair<SearchTargetMode, String> {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("Dev:", ignoreCase = true) -> {
                SearchTargetMode.DEV to trimmed.substring(4).trimStart()
            }
            trimmed.startsWith("Repo:", ignoreCase = true) -> {
                SearchTargetMode.REPO to trimmed.substring(5).trimStart()
            }
            else -> {
                currentMode to trimmed
            }
        }
    }

    val (activeSearchMode, rawSearchQuery) = extractRawQueryAndMode(searchQuery, searchTargetMode)

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            currentTimeMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(repos, githubPat) {
        if (repos.isNotEmpty()) {
            while (isActive) {
                gitHubViewModel.checkActiveWorkflowRuns(githubPat)
                delay(8000)
            }
        }
    }

    LaunchedEffect(githubPat) {
        if (githubPat.isNotEmpty() && activeOwnerFilter == null) {
            gitHubViewModel.fetchRepos(githubPat)
        }
    }

    val filteredRepos = remember(repos, searchQuery, activeSearchMode, rawSearchQuery) {
        val baseList = if (rawSearchQuery.isBlank()) {
            repos
        } else {
            repos.filter { repo ->
                if (activeSearchMode == SearchTargetMode.DEV) {
                    val ownerName = repo.owner?.login ?: ""
                    ownerName.contains(rawSearchQuery, ignoreCase = true) ||
                            repo.full_name.contains(rawSearchQuery, ignoreCase = true)
                } else {
                    repo.name.contains(rawSearchQuery, ignoreCase = true) ||
                            (repo.description?.contains(rawSearchQuery, ignoreCase = true) == true) ||
                            repo.full_name.contains(rawSearchQuery, ignoreCase = true)
                }
            }
        }
        baseList.sortedWith(
            compareByDescending<GitHubRepo> { repo ->
                repo.pushed_at ?: repo.updated_at ?: ""
            }.thenByDescending { repo ->
                repo.id
            }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "DevForge Repositories",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                if (activeOwnerFilter != null) "Viewing $activeOwnerFilter" else "${repos.size} repos connected",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showPublicLinkDialog = true }
                    ) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = "Explore Public Link",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            if (activeOwnerFilter != null) {
                                gitHubViewModel.fetchPublicRepoOrUser(activeOwnerFilter!!, githubPat)
                            } else if (githubPat.isNotEmpty()) {
                                gitHubViewModel.fetchRepos(githubPat)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {

            // GitHub PAT Token Prompt if not configured
            if (githubPat.isEmpty() && activeOwnerFilter == null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { onNavigateToSettings() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Set GitHub API Key in Settings to view private repos & trigger builds",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Active Filter Chip
            if (activeOwnerFilter != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Showing public filter: $activeOwnerFilter",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { gitHubViewModel.clearOwnerFilter(githubPat) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear filter",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // GitHub Target Mode Selector & Search Field
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // Selector options (Dev: vs Repo:)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "Search For:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Dev: Selection Option
                    FilterChip(
                        selected = activeSearchMode == SearchTargetMode.DEV,
                        onClick = {
                            searchTargetMode = SearchTargetMode.DEV
                            searchQuery = if (rawSearchQuery.isEmpty()) "Dev: " else "Dev: $rawSearchQuery"
                        },
                        label = {
                            Text("Dev:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Search Developer",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Repo: Selection Option
                    FilterChip(
                        selected = activeSearchMode == SearchTargetMode.REPO,
                        onClick = {
                            searchTargetMode = SearchTargetMode.REPO
                            searchQuery = if (rawSearchQuery.isEmpty()) "Repo: " else "Repo: $rawSearchQuery"
                        },
                        label = {
                            Text("Repo:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Folder,
                                contentDescription = "Search Repository",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // Search Input Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { newValue ->
                        searchQuery = newValue
                        val (detectedMode, _) = extractRawQueryAndMode(newValue, searchTargetMode)
                        searchTargetMode = detectedMode
                    },
                    placeholder = {
                        Text(
                            if (activeSearchMode == SearchTargetMode.DEV) "Dev: e.g. fourgeailabs"
                            else "Repo: e.g. DevForge"
                        )
                    },
                    leadingIcon = {
                        Icon(
                            if (activeSearchMode == SearchTargetMode.DEV) Icons.Default.Person else Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                            if (rawSearchQuery.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        gitHubViewModel.performGitHubSearch(activeSearchMode, rawSearchQuery, githubPat)
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = "Search GitHub",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (rawSearchQuery.isNotBlank()) {
                                gitHubViewModel.performGitHubSearch(activeSearchMode, rawSearchQuery, githubPat)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true
                )

                // Quick Action Trigger Banner for Live GitHub Search
                if (rawSearchQuery.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                gitHubViewModel.performGitHubSearch(activeSearchMode, rawSearchQuery, githubPat)
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (activeSearchMode == SearchTargetMode.DEV) "Search GitHub developers for '$rawSearchQuery' ->"
                                else "Search GitHub repositories for '$rawSearchQuery' ->",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading && repos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Fetching GitHub Repositories...")
                    }
                }
            } else if (error != null && repos.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Error fetching repos: $error",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { onNavigateToSettings() }) {
                            Text("Check Token Settings")
                        }
                    }
                }
            } else if (filteredRepos.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (searchQuery.isNotEmpty()) "No repositories found matching '$searchQuery'."
                        else "No repositories found on this account or filter.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredRepos) { repo ->
                        val activeRun = activeRuns[repo.full_name]
                        RepositoryCard(
                            repo = repo,
                            activeRun = activeRun,
                            currentTimeMs = currentTimeMs,
                            onClick = {
                                val parts = repo.full_name.split("/")
                                if (parts.size == 2) {
                                    onNavigateToRepoBuild(parts[0], parts[1])
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showPublicLinkDialog) {
            AlertDialog(
                onDismissRequest = { showPublicLinkDialog = false },
                title = { Text("Explore Public Repo / Creator") },
                text = {
                    Column {
                        Text(
                            "Paste any GitHub public repository link or creator profile page:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = publicUrlInput,
                            onValueChange = { publicUrlInput = it },
                            placeholder = { Text("e.g. https://github.com/fourgeailabs") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Examples:\n• https://github.com/fourgeailabs\n• fourgeailabs/DevForge",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (publicUrlInput.isNotBlank()) {
                                gitHubViewModel.fetchPublicRepoOrUser(publicUrlInput, githubPat)
                                showPublicLinkDialog = false
                            }
                        },
                        enabled = publicUrlInput.isNotBlank()
                    ) {
                        Text("Explore")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPublicLinkDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        AppUpdateDialog(
            updateState = updateState,
            onInstallNow = { info ->
                appUpdateViewModel.installUpdateNow(context, info, githubPat)
            },
            onSkipVersion = { versionTag ->
                appUpdateViewModel.skipVersion(versionTag)
            },
            onDismiss = {
                appUpdateViewModel.dismissState()
            }
        )
    }
}

private fun parseIsoToEpochMs(isoStr: String?): Long {
    if (isoStr.isNullOrEmpty()) return System.currentTimeMillis()
    return try {
        val cleanStr = isoStr.replace(Regex("\\.\\d+Z$"), "Z")
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = sdf.parse(cleanStr)
        date?.time ?: System.currentTimeMillis()
    } catch (_: Exception) {
        System.currentTimeMillis()
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

@Composable
fun RepositoryCard(
    repo: GitHubRepo,
    activeRun: GitHubWorkflowRun? = null,
    currentTimeMs: Long = System.currentTimeMillis(),
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = repo.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = repo.full_name,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = if (repo.private) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (repo.private) "Private" else "Public",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (repo.private) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (!repo.description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = repo.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            if (activeRun != null) {
                Spacer(modifier = Modifier.height(12.dp))

                val isQueued = activeRun.status == "queued" || activeRun.status == "waiting" || activeRun.status == "pending" || activeRun.status == "requested"
                val startMs = parseIsoToEpochMs(activeRun.run_started_at ?: activeRun.created_at)
                val elapsedSeconds = ((currentTimeMs - startMs) / 1000).coerceAtLeast(0)

                val estimatedTotalSeconds = 180L
                val remainingSeconds = estimatedTotalSeconds - elapsedSeconds

                val elapsedText = formatDuration(elapsedSeconds)
                val remainingText = if (isQueued) {
                    "Starting shortly..."
                } else if (remainingSeconds > 0) {
                    "~${formatDuration(remainingSeconds)} remaining"
                } else {
                    "Finalizing build..."
                }

                val progress = if (isQueued) 0.08f else (elapsedSeconds.toFloat() / estimatedTotalSeconds.toFloat()).coerceIn(0.10f, 0.95f)

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val alpha by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 1.0f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "alpha"
                                )

                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Action Processing",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeRun.name ?: "Build Android APK",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isQueued) "QUEUED" else "IN PROGRESS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isQueued) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Elapsed: $elapsedText",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.HourglassTop,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = remainingText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!repo.language.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = repo.language,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${repo.stargazers_count}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
