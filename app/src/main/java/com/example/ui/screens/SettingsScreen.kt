package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthViewModel
import com.example.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDarkMode by settingsViewModel.isDarkMode.collectAsState()
    val user by authViewModel.userState.collectAsState()

    var showWhatsNewSheet by remember { mutableStateOf(false) }
    var showPatHelpDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf("") }
    var tokenVisible by remember { mutableStateOf(false) }

    val savedGithubPat by settingsViewModel.githubPat.collectAsState()
    val isAuthenticating by authViewModel.isAuthenticating.collectAsState()
    val authError by authViewModel.authError.collectAsState()

    LaunchedEffect(savedGithubPat) {
        if (tokenInput.isEmpty()) {
            tokenInput = savedGithubPat
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings & About", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // GitHub API Key Authentication & Integrations
            item {
                Text(
                    "GitHub API Key & Authentication",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (user?.isLoggedIn == true) Color(0xFF2E7D32).copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (user?.isLoggedIn == true) Icons.Default.CheckCircle else Icons.Default.VpnKey,
                                    contentDescription = null,
                                    tint = if (user?.isLoggedIn == true) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (user?.isLoggedIn == true) "Authenticated as @${user?.uid}" else "Guest / Public Mode",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = if (user?.isLoggedIn == true) "Full API access enabled (Private repos & cloud builds)" else "GitHub token required to access private repositories",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "GitHub Personal Access Token (PAT)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            "Keys remain stored securely on your local device.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = tokenInput,
                            onValueChange = {
                                tokenInput = it
                                authViewModel.clearError()
                            },
                            label = { Text("Enter ghp_... token") },
                            placeholder = { Text("ghp_1234567890abcdef...") },
                            visualTransformation = if (tokenVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                                    Icon(
                                        if (tokenVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle Visibility"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        if (authError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = authError!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    settingsViewModel.setGithubPat(tokenInput)
                                    authViewModel.authenticateWithGitHub(tokenInput)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isAuthenticating && tokenInput.isNotBlank()
                            ) {
                                if (isAuthenticating) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Verifying...")
                                } else {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save & Authenticate")
                                }
                            }

                            if (user?.isLoggedIn == true || savedGithubPat.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = {
                                        tokenInput = ""
                                        settingsViewModel.setGithubPat("")
                                        authViewModel.signOut()
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Disconnect")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { showPatHelpDialog = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("How to create a GitHub Personal Access Token?", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Gemini API Key (BYOK)
            item {
                Text(
                    "Gemini AI Key (BYOK)",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        val geminiKey by settingsViewModel.geminiApiKey.collectAsState()

                        Text("Gemini API Key for AI Completion", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Used for AI build finish predictions and code review tools.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = { settingsViewModel.setGeminiApiKey(it) },
                            label = { Text("Gemini API Key (Optional)") },
                            placeholder = { Text("AIzaSy...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // Preferences
            item {
                Text(
                    "Preferences",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Dark Theme", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
                        }
                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = { settingsViewModel.toggleDarkMode(it) }
                        )
                    }
                }
            }

            // What's New & Updates
            item {
                OutlinedButton(
                    onClick = { showWhatsNewSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("What's New in this Update", fontWeight = FontWeight.SemiBold)
                }
            }

            // About Section
            item {
                Text(
                    "About",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("App Name: DevForge Pro", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Version: 1.04.00", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Creator:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/fourgeailabs"))
                                    context.startActivity(intent)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                "FourgeAI LABS",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = "Open GitHub",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("App GitHub Repository:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/fourgeailabs/DevForge"))
                                    context.startActivity(intent)
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                "github.com/fourgeailabs/DevForge",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = "Open Repo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showWhatsNewSheet) {
            ModalBottomSheet(
                onDismissRequest = { showWhatsNewSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                WhatsNewContent()
            }
        }

        if (showPatHelpDialog) {
            AlertDialog(
                onDismissRequest = { showPatHelpDialog = false },
                title = { Text("How to get a GitHub Token") },
                text = {
                    Column {
                        Text(
                            "Follow these steps on GitHub to generate a Personal Access Token:",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("1. Open github.com/settings/tokens in your browser.", fontSize = 12.sp)
                        Text("2. Click 'Generate new token' -> 'Generate new token (classic)'.", fontSize = 12.sp)
                        Text("3. Give it a name (e.g., 'DevForge Mobile').", fontSize = 12.sp)
                        Text("4. Under Scopes, select 'repo' and 'workflow'.", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("5. Click 'Generate token' and copy the token string.", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open GitHub Token Settings")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPatHelpDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun WhatsNewContent() {
    var expandedVersion by remember { mutableStateOf<String?>("1.04.00") }

    val updates = listOf(
        UpdateItem(
            version = "1.04.00",
            date = "Current Update",
            notes = listOf(
                "Prioritized Pre-Built APK Installer: Automatic detection of unzipped .apk release assets uploaded by creators to GitHub, prioritizing them above zipped build artifacts.",
                "Purged Mandatory Login Screen: Full guest-first experience with instant dashboard access upon launch.",
                "Settings API Key & Authentication Hub: Integrated GitHub Personal Access Token authentication directly in Settings with step-by-step guidance.",
                "Top Dashboard Search Bar: Prominently positioned repository & creator URL explorer directly at the top of the home screen."
            )
        ),
        UpdateItem(
            version = "1.03.00",
            date = "Previous Update",
            notes = listOf(
                "Public Repository & Creator Link Explorer: Option in login menu & dashboard allowing access to public repos by pasting any GitHub link or creator handle.",
                "AI Studio Export Integration: Guidance and workflow helper for automatically syncing Google AI Studio projects to GitHub repos and building mobile APKs.",
                "Robust APK Extraction Engine: Streamlined ZipFile extractor preventing archive unpack failures on all Android devices.",
                "Accurate Build Timer: Prioritized run_started_at timestamps for precise elapsed time calculation on re-runs."
            )
        ),
        UpdateItem(
            version = "1.02.00",
            date = "Previous Update",
            notes = listOf(
                "Pull to Refresh: Swipe down on repository actions to manually re-fetch build logs and artifact status.",
                "Rerun Build Feature: Direct 1-tap re-run button for the most recent GitHub Actions workflow run.",
                "Gemini AI Completion Predictions: Real-time AI build finish time predictions using Gemini 3.5 Flash.",
                "Automatic ZIP Artifact Installer: Download and extract repository-level APK artifacts automatically."
            )
        ),
        UpdateItem(
            version = "1.01.00",
            date = "Previous Update",
            notes = listOf(
                "GitHub Repository Browser: Select any of your GitHub repositories directly.",
                "GitHub Actions APK Builder: Trigger build.yml workflows from mobile to generate APKs in cloud.",
                "Live Build Progress: Monitor queued, in-progress, and finished APK builds.",
                "Automatic APK Sideloading: Unzip artifact archives and launch native Android package installer.",
                "What's New & About Menus: Added release notes accordion and FourgeAI LABS credentials."
            )
        ),
        UpdateItem(
            version = "1.00.00",
            date = "Initial Release",
            notes = listOf(
                "Gemini AI integration for code reviews and auto-edits.",
                "Personal Access Token & BYOK Key storage using Android DataStore.",
                "Dark & Light Material 3 theme styling."
            )
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.NewReleases, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "What's New in DevForge Pro",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        updates.forEach { update ->
            val isExpanded = expandedVersion == update.version

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable {
                        expandedVersion = if (isExpanded) null else update.version
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                "Version ${update.version}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                update.date,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Toggle"
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(8.dp))
                            update.notes.forEach { note ->
                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text("• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

data class UpdateItem(
    val version: String,
    val date: String,
    val notes: List<String>
)
