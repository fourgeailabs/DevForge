package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.auth.AuthViewModel
import com.example.settings.SettingsViewModel
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.RepoBuildScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private val authViewModel: AuthViewModel by viewModels()
  private val settingsViewModel: SettingsViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val isDarkMode by settingsViewModel.isDarkMode.collectAsState()
      val userState by authViewModel.userState.collectAsState()

      MyApplicationTheme(darkTheme = isDarkMode) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            val navController = rememberNavController()
            val startDest = if (userState != null) "dashboard" else "login"
            
            NavHost(navController = navController, startDestination = startDest) {
                composable("login") {
                    LoginScreen(
                        authViewModel = authViewModel,
                        onNavigateToDashboard = {
                            navController.navigate("dashboard") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    )
                }
                composable("dashboard") {
                    DashboardScreen(
                        onNavigateToRepoBuild = { owner, repo ->
                            navController.navigate("repo_build/$owner/$repo")
                        },
                        onNavigateToSettings = {
                            navController.navigate("settings")
                        }
                    )
                }
                composable("repo_build/{owner}/{repo}") { backStackEntry ->
                    val owner = backStackEntry.arguments?.getString("owner") ?: ""
                    val repo = backStackEntry.arguments?.getString("repo") ?: ""
                    RepoBuildScreen(
                        owner = owner,
                        repo = repo,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToEditor = {
                            navController.navigate("editor/$owner-$repo")
                        }
                    )
                }
                composable("editor/{projectId}") { backStackEntry ->
                    val projectId = backStackEntry.arguments?.getString("projectId") ?: "default"
                    EditorScreen(
                        projectId = projectId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        authViewModel = authViewModel,
                        settingsViewModel = settingsViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onSignOut = {
                            navController.navigate("login") {
                                popUpTo("dashboard") { inclusive = true }
                            }
                        }
                    )
                }
            }
        }
      }
    }
  }
}
