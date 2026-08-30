package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.viewmodel.AppUpdateInfo
import com.example.viewmodel.AppUpdateState
import com.example.viewmodel.UpdateSourceType

@Composable
fun AppUpdateDialog(
    updateState: AppUpdateState,
    onInstallNow: (AppUpdateInfo) -> Unit,
    onSkipVersion: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    when (updateState) {
        is AppUpdateState.Available -> {
            val info = updateState.updateInfo
            Dialog(
                onDismissRequest = onDismiss,
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (info.sourceType == UpdateSourceType.GITHUB_ACTIONS)
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        else
                                            MaterialTheme.colorScheme.primaryContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (info.sourceType == UpdateSourceType.GITHUB_ACTIONS) Icons.Default.Build else Icons.Default.SystemUpdate,
                                    contentDescription = null,
                                    tint = if (info.sourceType == UpdateSourceType.GITHUB_ACTIONS) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Update Available",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = info.title,
                                    fontSize = 13.sp,
                                    color = if (info.sourceType == UpdateSourceType.GITHUB_ACTIONS) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Update Source Badge
                        Surface(
                            color = if (info.sourceType == UpdateSourceType.GITHUB_ACTIONS)
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (info.sourceType == UpdateSourceType.GITHUB_ACTIONS) Icons.Default.Build else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (info.sourceType == UpdateSourceType.GITHUB_ACTIONS) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (info.sourceType == UpdateSourceType.GITHUB_ACTIONS)
                                        "Source: GitHub Actions Tab (Workflow Artifact)"
                                    else
                                        "Source: GitHub Releases Tab",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (info.sourceType == UpdateSourceType.GITHUB_ACTIONS)
                                        MaterialTheme.colorScheme.onTertiaryContainer
                                    else
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Release Notes / Commit info Card
                        Text(
                            text = if (info.sourceType == UpdateSourceType.GITHUB_ACTIONS) "Workflow Details & Changes:" else "What's New in ${info.tagName}:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = info.releaseNotes,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Link to GitHub Release / Actions Run
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (info.sourceType == UpdateSourceType.GITHUB_ACTIONS) "View Run on Actions Tab" else "View Update on GitHub",
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Actions: Install Now, Skip This Version, Remind Me Later
                        Button(
                            onClick = { onInstallNow(info) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (info.sourceType == UpdateSourceType.GITHUB_ACTIONS) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                contentColor = if (info.sourceType == UpdateSourceType.GITHUB_ACTIONS) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Install Now", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(
                                onClick = { onSkipVersion(info.tagName) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Skip Version",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }

                            TextButton(
                                onClick = onDismiss,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Remind Me Later",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        is AppUpdateState.DownloadProgress -> {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = updateState.message,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        is AppUpdateState.UpToDate -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("App is Up to Date") },
                text = { Text("You are running the latest version of DevForge Pro (v${updateState.currentVersion}).") },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            )
        }

        is AppUpdateState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Update Notice") },
                text = { Text(updateState.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("OK")
                    }
                }
            )
        }

        is AppUpdateState.Checking -> {
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Checking GitHub for updates...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        AppUpdateState.Idle -> {
            // Do nothing
        }
    }
}
