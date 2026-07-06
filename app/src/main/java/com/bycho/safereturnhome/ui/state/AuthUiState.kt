package com.bycho.safereturnhome.ui.state

data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isRegisterMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val signedInEmail: String? = null,
    val isEmailVerificationPending: Boolean = false,
    val isFirebaseConfigured: Boolean = true,
    val message: String? = null,
    val errorMessage: String? = null
)
