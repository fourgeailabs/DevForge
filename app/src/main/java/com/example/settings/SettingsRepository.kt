package com.example.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    companion object {
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val SELECTED_AI_PROVIDER_KEY = stringPreferencesKey("selected_ai_provider")
        val GITHUB_PAT_KEY = stringPreferencesKey("github_pat_key")
        val SKIPPED_UPDATE_VERSION_KEY = stringPreferencesKey("skipped_update_version")
    }

    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DARK_MODE_KEY] ?: true // Default to true for High Density theme
        }
        
    val selectedAiProvider: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SELECTED_AI_PROVIDER_KEY] ?: "Google Gemini"
        }

    val geminiApiKey: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[GEMINI_API_KEY] ?: ""
        }
        
    fun getAiApiKeyForProvider(provider: String): Flow<String> = context.dataStore.data
        .map { preferences ->
            val providerKey = stringPreferencesKey("ai_key_${provider.lowercase().replace(" ", "_")}")
            val key = preferences[providerKey]
            if (!key.isNullOrEmpty()) key else (preferences[GEMINI_API_KEY] ?: "")
        }

    val githubPat: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[GITHUB_PAT_KEY] ?: ""
        }

    val skippedUpdateVersion: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[SKIPPED_UPDATE_VERSION_KEY] ?: ""
        }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }

    suspend fun setSelectedAiProvider(provider: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_AI_PROVIDER_KEY] = provider
        }
    }

    suspend fun setAiApiKey(provider: String, key: String) {
        context.dataStore.edit { preferences ->
            val providerKey = stringPreferencesKey("ai_key_${provider.lowercase().replace(" ", "_")}")
            preferences[providerKey] = key
            preferences[GEMINI_API_KEY] = key
        }
    }

    suspend fun setGeminiApiKey(key: String) {
        setAiApiKey("Google Gemini", key)
    }

    suspend fun setGithubPat(token: String) {
        context.dataStore.edit { preferences ->
            preferences[GITHUB_PAT_KEY] = token
        }
    }

    suspend fun setSkippedUpdateVersion(version: String) {
        context.dataStore.edit { preferences ->
            preferences[SKIPPED_UPDATE_VERSION_KEY] = version
        }
    }
}
