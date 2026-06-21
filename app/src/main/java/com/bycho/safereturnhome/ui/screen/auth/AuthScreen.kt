package com.bycho.safereturnhome.ui.screen.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bycho.safereturnhome.ui.state.AuthUiState
import com.bycho.safereturnhome.ui.viewmodel.AuthViewModel

@Composable
fun AuthRoute(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.isSignedIn) {
        if (uiState.isSignedIn) {
            onAuthenticated()
        }
    }
    AuthScreen(
        uiState = uiState,
        onEmailChanged = viewModel::onEmailChanged,
        onPasswordChanged = viewModel::onPasswordChanged,
        onConfirmPasswordChanged = viewModel::onConfirmPasswordChanged,
        onSubmit = viewModel::submit,
        onToggleMode = viewModel::toggleMode,
        onCheckEmailVerification = viewModel::checkEmailVerification,
        onResendVerificationEmail = viewModel::resendVerificationEmail
    )
}

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onConfirmPasswordChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit,
    onCheckEmailVerification: () -> Unit,
    onResendVerificationEmail: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "안심 귀가",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = if (uiState.isRegisterMode) {
                    "회원가입 후 안전 귀가 기능을 시작하세요."
                } else {
                    "로그인해서 안전 귀가 기능을 시작하세요."
                },
                style = MaterialTheme.typography.bodyLarge
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiState.email,
                        onValueChange = onEmailChanged,
                        label = { Text("이메일") },
                        singleLine = true,
                        enabled = uiState.isFirebaseConfigured && !uiState.isLoading,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiState.password,
                        onValueChange = onPasswordChanged,
                        label = { Text("비밀번호") },
                        singleLine = true,
                        enabled = uiState.isFirebaseConfigured && !uiState.isLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    if (uiState.isRegisterMode) {
                        OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = uiState.confirmPassword,
                            onValueChange = onConfirmPasswordChanged,
                            label = { Text("비밀번호 확인") },
                            singleLine = true,
                            enabled = uiState.isFirebaseConfigured && !uiState.isLoading,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.isFirebaseConfigured && !uiState.isLoading,
                        onClick = onSubmit
                    ) {
                        Text(
                            when {
                                uiState.isLoading -> "처리 중..."
                                uiState.isRegisterMode -> "회원가입"
                                else -> "로그인"
                            }
                        )
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading,
                        onClick = onToggleMode
                    ) {
                        Text(
                            if (uiState.isRegisterMode) {
                                "이미 계정이 있으면 로그인"
                            } else {
                                "계정이 없으면 회원가입"
                            }
                        )
                    }
                }
            }

            if (uiState.isEmailVerificationPending) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "이메일 인증 대기 중",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${uiState.signedInEmail ?: uiState.email}로 보낸 인증 링크를 누른 뒤 아래 버튼을 눌러주세요."
                        )
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading,
                            onClick = onCheckEmailVerification
                        ) {
                            Text(if (uiState.isLoading) "확인 중..." else "인증 완료 확인")
                        }
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading,
                            onClick = onResendVerificationEmail
                        ) {
                            Text("인증 메일 다시 보내기")
                        }
                    }
                }
            }

            uiState.message?.let { message ->
                Text(text = message)
            }
            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (!uiState.isFirebaseConfigured) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Firebase 설정 필요",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Firebase 콘솔에서 Android 앱을 등록하고 google-services.json을 app 폴더에 넣어야 로그인과 회원가입이 동작합니다."
                        )
                    }
                }
            }
        }
    }
}
