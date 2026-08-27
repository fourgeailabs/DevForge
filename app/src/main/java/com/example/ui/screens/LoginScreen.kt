package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val userState by authViewModel.userState.collectAsState()
    val authError by authViewModel.authError.collectAsState()
    val isAuthenticating by authViewModel.isAuthenticating.collectAsState()

    var showGuideDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userState) {
        if (userState != null) {
            onNavigateToDashboard()
        }
    }

    var token by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Code,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "DevForge Pro",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Sign in with GitHub to access repos",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (authError != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = authError ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            OutlinedTextField(
                value = token,
                onValueChange = { 
                    token = it
                    authViewModel.clearError() 
                },
                label = { Text("GitHub Personal Access Token") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = { showGuideDialog = true }
            ) {
                Icon(Icons.Default.HelpOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Where do I get a personal access token?", fontSize = 13.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { authViewModel.authenticateWithGitHub(token) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = token.isNotBlank() && !isAuthenticating
            ) {
                if (isAuthenticating) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Sign in with GitHub", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Your token is stored securely on your device and only used to access your GitHub repositories and run Actions.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        if (showGuideDialog) {
            AlertDialog(
                onDismissRequest = { showGuideDialog = false },
                title = { Text("How to get a GitHub Token") },
                text = {
                    Column {
                        Text("1. Open GitHub Tokens page in your browser.", fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("2. Click 'Generate new token' -> 'Classic'.", fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("3. Give it a note (e.g. DevForge Mobile).", fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("4. Select required scopes:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("   • 'repo' (Full control of repositories)", fontSize = 12.sp)
                        Text("   • 'workflow' (Trigger GitHub Actions)", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("5. Click 'Generate token' and copy string starting with 'ghp_'.", fontSize = 13.sp)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/tokens/new?description=DevForge%20Mobile&scopes=repo,workflow"))
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Generate Token on GitHub")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGuideDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}
