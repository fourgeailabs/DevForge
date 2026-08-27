package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.network.Content
import com.example.network.GenerateContentRequest
import com.example.network.Part
import com.example.network.RetrofitClient
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EditorViewModel : ViewModel() {

    private val db: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        null
    }
    private var currentProjectId: String? = null
    private var isSyncing = false

    private val _codeContent = MutableStateFlow("fun main() {\n    println(\"Hello, DevStudio!\")\n}")
    val codeContent: StateFlow<String> = _codeContent.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _geminiFeedback = MutableStateFlow<String?>(null)
    val geminiFeedback: StateFlow<String?> = _geminiFeedback.asStateFlow()

    fun initializeProject(projectId: String) {
        currentProjectId = projectId
        db?.collection("projects")?.document(projectId)?.addSnapshotListener { snapshot, e ->
            if (e != null || snapshot == null) return@addSnapshotListener
            if (!isSyncing) {
                val remoteCode = snapshot.getString("content")
                if (remoteCode != null && remoteCode != _codeContent.value) {
                    _codeContent.value = remoteCode
                }
            }
        }
    }

    fun updateCode(newCode: String) {
        _codeContent.value = newCode
        currentProjectId?.let { projectId ->
            isSyncing = true
            db?.collection("projects")?.document(projectId)
                ?.set(mapOf("content" to newCode), SetOptions.merge())
                ?.addOnCompleteListener { isSyncing = false }
        }
    }

    private val _hasDetectedErrors = MutableStateFlow(false)
    val hasDetectedErrors: StateFlow<Boolean> = _hasDetectedErrors.asStateFlow()

    fun analyzeCodeWithGemini(apiKey: String) {
        if (apiKey.isEmpty()) {
            _geminiFeedback.value = "Gemini API key is not configured in Settings."
            return
        }

        viewModelScope.launch {
            _isAnalyzing.value = true
            _geminiFeedback.value = null
            _hasDetectedErrors.value = false
            try {
                val prompt = "Please review the following code for any errors. If there are errors, start your response with 'ERRORS_DETECTED'. Then summarize the errors and suggest improvements:\n\n${_codeContent.value}"
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt))))
                )
                val response = RetrofitClient.geminiService.generateContent(apiKey, request)
                val feedback = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "No feedback provided by Gemini."
                
                if (feedback.contains("ERRORS_DETECTED")) {
                    _hasDetectedErrors.value = true
                    _geminiFeedback.value = feedback.replace("ERRORS_DETECTED", "").trim()
                } else {
                    _geminiFeedback.value = feedback
                }
            } catch (e: Exception) {
                _geminiFeedback.value = "Error analyzing code: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun fixErrorsWithGemini(apiKey: String) {
        if (apiKey.isEmpty()) {
            _geminiFeedback.value = "Gemini API key is not configured in Settings."
            return
        }

        viewModelScope.launch {
            _isAnalyzing.value = true
            try {
                val prompt = "You are an expert developer. Fix any errors in the following code and return ONLY the corrected code without markdown blocks or explanations:\n\n${_codeContent.value}"
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = prompt))))
                )
                val response = RetrofitClient.geminiService.generateContent(apiKey, request)
                val fixedCode = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (fixedCode != null) {
                    _codeContent.value = fixedCode.replace("```kotlin", "").replace("```", "").trim()
                    _geminiFeedback.value = "Code updated with Gemini's fixes."
                } else {
                    _geminiFeedback.value = "Gemini could not provide a fix."
                }
            } catch (e: Exception) {
                _geminiFeedback.value = "Error fixing code: ${e.message}"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun clearFeedback() {
        _geminiFeedback.value = null
    }
}
