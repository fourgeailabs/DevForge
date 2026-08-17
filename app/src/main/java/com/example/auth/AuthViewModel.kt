package com.example.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.BuildConfig

class AuthViewModel : ViewModel() {
    private val auth: FirebaseAuth? = try {
        Firebase.auth
    } catch (e: Exception) {
        null
    }

    private val _userState = MutableStateFlow<User?>(null)
    val userState: StateFlow<User?> = _userState.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    init {
        if (auth == null) {
            _authError.value = "Firebase is not configured. Missing google-services.json."
        } else {
            auth.addAuthStateListener { firebaseAuth ->
                val user = firebaseAuth.currentUser
                _userState.value = if (user != null) {
                    User(user.uid, user.displayName ?: "Developer", user.email ?: "", true)
                } else null
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        if (auth == null) {
            _authError.value = "Firebase is not configured. Missing google-services.json."
            return
        }
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val webClientId = BuildConfig.WEB_CLIENT_ID
                if (webClientId.isEmpty()) {
                    _authError.value = "WEB_CLIENT_ID not configured in Secrets."
                    return@launch
                }

                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(context = context, request = request)
                handleSignIn(result)
            } catch (e: GetCredentialException) {
                _authError.value = "Sign-in failed: ${e.message}"
            } catch (e: Exception) {
                _authError.value = "An error occurred: ${e.message}"
            }
        }
    }

    private suspend fun handleSignIn(result: GetCredentialResponse) {
        when (val credential = result.credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                        firebaseAuthWithGoogle(authCredential)
                    } catch (e: GoogleIdTokenParsingException) {
                        _authError.value = "Invalid Google ID token."
                    }
                }
            }
            else -> {
                _authError.value = "Unexpected credential type."
            }
        }
    }

    private suspend fun firebaseAuthWithGoogle(credential: AuthCredential) {
        if (auth == null) return
        try {
            auth.signInWithCredential(credential).await()
            _authError.value = null
        } catch (e: Exception) {
            _authError.value = "Firebase Auth failed: ${e.message}"
        }
    }

    fun signOut() {
        auth?.signOut()
        _userState.value = null
    }
    
    fun signInAsGuest() {
        _userState.value = User("guest", "Local User", "Offline Mode", false)
        _authError.value = null
    }
    
    fun clearError() {
        _authError.value = null
    }
}

data class User(val uid: String, val name: String, val email: String, val isLoggedIn: Boolean)
