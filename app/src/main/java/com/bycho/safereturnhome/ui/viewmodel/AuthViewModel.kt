package com.bycho.safereturnhome.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.bycho.safereturnhome.ui.state.AuthUiState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthViewModel : ViewModel() {
    private val firebaseAuth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
    private val authStateListener = FirebaseAuth.AuthStateListener { auth ->
        val user = auth.currentUser
        val isEmailVerified = user?.isEmailVerified == true
        _uiState.value = _uiState.value.copy(
            isSignedIn = isEmailVerified,
            signedInEmail = user?.email,
            isEmailVerificationPending = user != null && !isEmailVerified,
            isLoading = false,
            errorMessage = null
        )
    }

    private val _uiState = MutableStateFlow(
        AuthUiState(
            isFirebaseConfigured = firebaseAuth != null,
            isSignedIn = firebaseAuth?.currentUser?.isEmailVerified == true,
            signedInEmail = firebaseAuth?.currentUser?.email,
            isEmailVerificationPending = firebaseAuth?.currentUser?.let { !it.isEmailVerified } ?: false,
            errorMessage = if (firebaseAuth == null) {
                "Firebase 설정 파일이 없습니다. app/google-services.json을 추가한 뒤 다시 실행해 주세요."
            } else {
                null
            }
        )
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        firebaseAuth?.addAuthStateListener(authStateListener)
    }

    fun onEmailChanged(email: String) {
        _uiState.value = _uiState.value.copy(email = email, errorMessage = null)
    }

    fun onPasswordChanged(password: String) {
        _uiState.value = _uiState.value.copy(password = password, errorMessage = null)
    }

    fun onConfirmPasswordChanged(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = confirmPassword, errorMessage = null)
    }

    fun toggleMode() {
        _uiState.value = _uiState.value.copy(
            isRegisterMode = !_uiState.value.isRegisterMode,
            password = "",
            confirmPassword = "",
            message = null,
            errorMessage = null
        )
    }

    fun submit() {
        val auth = firebaseAuth
        if (auth == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Firebase 설정이 완료되지 않았습니다."
            )
            return
        }

        val state = _uiState.value
        val email = state.email.trim()
        val password = state.password
        val validationError = validate(email, password, state.confirmPassword, state.isRegisterMode)
        if (validationError != null) {
            _uiState.value = state.copy(errorMessage = validationError)
            return
        }

        _uiState.value = state.copy(
            email = email,
            isLoading = true,
            message = null,
            errorMessage = null
        )

        val task = if (state.isRegisterMode) {
            auth.createUserWithEmailAndPassword(email, password)
        } else {
            auth.signInWithEmailAndPassword(email, password)
        }

        task.addOnCompleteListener { result ->
            if (result.isSuccessful) {
                val user = result.result?.user ?: auth.currentUser
                if (state.isRegisterMode) {
                    if (user == null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            password = "",
                            confirmPassword = "",
                            errorMessage = "가입된 사용자 정보를 확인하지 못했습니다. 다시 로그인해 주세요."
                        )
                    } else {
                        sendVerificationEmail(
                            user = user,
                            successMessage = "인증 메일을 보냈습니다. 메일함에서 인증 링크를 눌러주세요."
                        )
                    }
                } else if (user?.isEmailVerified == true) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedIn = true,
                        signedInEmail = user.email,
                        isEmailVerificationPending = false,
                        password = "",
                        confirmPassword = "",
                        message = "로그인되었습니다.",
                        errorMessage = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedIn = false,
                        signedInEmail = user?.email,
                        isEmailVerificationPending = user != null,
                        password = "",
                        confirmPassword = "",
                        message = "이메일 인증 후 이용할 수 있습니다. 메일함에서 인증 링크를 눌러주세요.",
                        errorMessage = null
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exception?.localizedMessage
                        ?: "인증 요청을 처리하지 못했습니다."
                )
            }
        }
    }

    fun checkEmailVerification() {
        val auth = firebaseAuth
        val user = auth?.currentUser
        if (user == null) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isSignedIn = false,
                isEmailVerificationPending = false,
                errorMessage = "인증을 확인할 사용자 정보가 없습니다. 다시 로그인해 주세요."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            message = null,
            errorMessage = null
        )

        user.reload().addOnCompleteListener { result ->
            if (result.isSuccessful) {
                val refreshedUser = auth.currentUser
                if (refreshedUser?.isEmailVerified == true) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedIn = true,
                        signedInEmail = refreshedUser.email,
                        isEmailVerificationPending = false,
                        password = "",
                        confirmPassword = "",
                        message = "이메일 인증이 완료되었습니다.",
                        errorMessage = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isSignedIn = false,
                        signedInEmail = refreshedUser?.email ?: user.email,
                        isEmailVerificationPending = true,
                        message = null,
                        errorMessage = "아직 이메일 인증이 완료되지 않았습니다."
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exception?.localizedMessage
                        ?: "이메일 인증 상태를 확인하지 못했습니다."
                )
            }
        }
    }

    fun resendVerificationEmail() {
        val user = firebaseAuth?.currentUser
        if (user == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "인증 메일을 보낼 사용자 정보가 없습니다. 다시 로그인해 주세요."
            )
            return
        }
        if (user.isEmailVerified) {
            _uiState.value = _uiState.value.copy(
                isSignedIn = true,
                signedInEmail = user.email,
                isEmailVerificationPending = false,
                message = "이미 이메일 인증이 완료되었습니다.",
                errorMessage = null
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            message = null,
            errorMessage = null
        )
        sendVerificationEmail(
            user = user,
            successMessage = "인증 메일을 다시 보냈습니다. 메일함에서 인증 링크를 확인해 주세요."
        )
    }

    fun signOut() {
        firebaseAuth?.signOut()
        _uiState.value = _uiState.value.copy(
            isSignedIn = false,
            signedInEmail = null,
            isEmailVerificationPending = false,
            password = "",
            confirmPassword = "",
            message = "로그아웃되었습니다.",
            errorMessage = null
        )
    }

    override fun onCleared() {
        firebaseAuth?.removeAuthStateListener(authStateListener)
        super.onCleared()
    }

    private fun validate(
        email: String,
        password: String,
        confirmPassword: String,
        isRegisterMode: Boolean
    ): String? {
        if (email.isBlank()) return "이메일을 입력해 주세요."
        if (!email.contains("@")) return "올바른 이메일 형식이 아닙니다."
        if (password.length < 6) return "비밀번호는 6자 이상이어야 합니다."
        if (isRegisterMode && password != confirmPassword) {
            return "비밀번호 확인이 일치하지 않습니다."
        }
        return null
    }

    private fun sendVerificationEmail(
        user: FirebaseUser,
        successMessage: String
    ) {
        user.sendEmailVerification().addOnCompleteListener { result ->
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isSignedIn = false,
                signedInEmail = user.email,
                isEmailVerificationPending = true,
                password = "",
                confirmPassword = "",
                message = if (result.isSuccessful) successMessage else null,
                errorMessage = if (result.isSuccessful) {
                    null
                } else {
                    result.exception?.localizedMessage ?: "인증 메일을 보내지 못했습니다."
                }
            )
        }
    }
}
