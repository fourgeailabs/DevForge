package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.settings.SettingsViewModel
import com.example.viewmodel.CompileStatus
import com.example.viewmodel.EditorViewModel
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    projectId: String,
    onNavigateBack: () -> Unit,
    viewModel: EditorViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val codeContent by viewModel.codeContent.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val geminiFeedback by viewModel.geminiFeedback.collectAsState()
    val hasDetectedErrors by viewModel.hasDetectedErrors.collectAsState()
    val geminiApiKey by settingsViewModel.geminiApiKey.collectAsState()
    val githubPat by settingsViewModel.githubPat.collectAsState()
    val compileStatus by viewModel.compileStatus.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.initializeProject(projectId)
    }

    var showCompileDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            "Editor", 
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                        Text(
                            "main.kt", 
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                },
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
                actions = {
                    IconButton(
                        onClick = { viewModel.analyzeCodeWithGemini(geminiApiKey) },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            Icons.Default.CheckCircle, 
                            contentDescription = "Review Code",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(
                        onClick = { showCompileDialog = true },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            Icons.Default.Build, 
                            contentDescription = "Compile APK",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp
                )
        ) {
            // Editor Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                BasicTextField(
                    value = codeContent,
                    onValueChange = { viewModel.updateCode(it) },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Gemini Feedback Area
            if (isAnalyzing || geminiFeedback != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
                ) {
                    Column {
                        // AI Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AutoFixHigh,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "GEMINI AI ASSISTANT",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            TextButton(
                                onClick = { viewModel.clearFeedback() },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(20.dp)
                            ) {
                                Text("Dismiss", fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground)
                            }
                        }
                        
                        // Content
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            if (isAnalyzing) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Gemini is analyzing your code...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                                }
                            } else {
                                Column {
                                    Text(
                                        text = if (hasDetectedErrors) "Errors detected. Would you like to fix them manually, or let Gemini auto-repair?" else (geminiFeedback ?: ""),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            lineHeight = 16.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 150.dp)
                                            .verticalScroll(rememberScrollState())
                                    )
                                    
                                    if (hasDetectedErrors) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.clearFeedback() },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.padding(end = 8.dp)
                                            ) {
                                                Text("Fix Manually", color = MaterialTheme.colorScheme.onBackground)
                                            }
                                            Button(
                                                onClick = { viewModel.fixErrorsWithGemini(geminiApiKey) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Apply AI Fix", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCompileDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (compileStatus !is CompileStatus.Compiling) {
                        showCompileDialog = false
                        viewModel.resetCompilation()
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Compile & Build APK", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        when (val status = compileStatus) {
                            is CompileStatus.Idle -> {
                                Text(
                                    text = "Compile main.kt source code into a signed Android APK using the DevForge build engine.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Target Architecture: ARM64-v8a / x86_64", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        Text("Compiler: Kotlinc v2.0 + R8 DEX", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (githubPat.isNotBlank()) {
                                            Text("GitHub Action Integration: Active (Will trigger remote workflow)", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                            is CompileStatus.Compiling -> {
                                Text(
                                    text = status.stage,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { status.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Terminal,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Build Console", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .padding(8.dp)
                                ) {
                                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                        status.logs.forEach { log ->
                                            Text(
                                                text = log,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = if (log.contains("SUCCESS") || log.contains("OK")) Color(0xFF4CAF50) else Color(0xFFD4D4D4)
                                            )
                                        }
                                    }
                                }
                            }
                            is CompileStatus.Success -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Build Completed Successfully!",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF4CAF50)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Package: ${status.apkFileName}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("Size: ${status.fileSize} • Build Duration: ${status.buildDurationSec}s", fontSize = 11.sp)
                                        if (status.remoteTriggered) {
                                            Text("GitHub Action Remote Build: Triggered", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF1E1E1E))
                                        .padding(8.dp)
                                ) {
                                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                        status.logs.forEach { log ->
                                            Text(
                                                text = log,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = if (log.contains("SUCCESS")) Color(0xFF4CAF50) else Color(0xFFD4D4D4)
                                            )
                                        }
                                    }
                                }
                            }
                            is CompileStatus.Failed -> {
                                Text(
                                    text = "Build Error: ${status.error}",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    when (val status = compileStatus) {
                        is CompileStatus.Idle -> {
                            Button(
                                onClick = {
                                    viewModel.startCompilation(githubPat = githubPat, ownerRepo = projectId)
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Start Build", fontWeight = FontWeight.Bold)
                            }
                        }
                        is CompileStatus.Compiling -> {
                            // Disabled while compiling
                        }
                        is CompileStatus.Success -> {
                            Button(
                                onClick = {
                                    Toast.makeText(context, "APK build target ready: ${status.apkFileName}", Toast.LENGTH_LONG).show()
                                    showCompileDialog = false
                                    viewModel.resetCompilation()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download APK", fontWeight = FontWeight.Bold)
                            }
                        }
                        is CompileStatus.Failed -> {
                            Button(
                                onClick = {
                                    viewModel.startCompilation(githubPat = githubPat, ownerRepo = projectId)
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Retry Build", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            if (compileStatus !is CompileStatus.Compiling) {
                                showCompileDialog = false
                                viewModel.resetCompilation()
                            }
                        }
                    ) {
                        Text(if (compileStatus is CompileStatus.Success) "Close" else "Cancel", color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            )
        }
    }
}

