package com.bycho.safereturnhome.ui.screen.guardian

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bycho.safereturnhome.R
import com.bycho.safereturnhome.data.GuardianPreferences
import com.bycho.safereturnhome.data.guardianPhoneNumberError
import com.bycho.safereturnhome.data.normalizeGuardianPhoneNumber
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

private const val GUARDIAN_PHONE_AUTH_APP_NAME = "guardian-phone-auth"

@Composable
fun GuardianSettingsRoute(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val guardianPreferences = remember(context) { GuardianPreferences(context) }
    val guardianPhoneAuth = remember(context) { runCatching { getGuardianPhoneAuth(context) }.getOrNull() }
    var phoneNumber by remember { mutableStateOf(guardianPreferences.getPhoneNumber()) }
    var smsPhoneNumber by remember {
        mutableStateOf(guardianPreferences.getSmsPhoneNumber().ifBlank { guardianPreferences.getPhoneNumber() })
    }
    var verificationCode by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var verificationPhoneNumber by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var isCodeSent by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember {
        mutableStateOf(
            if (guardianPreferences.isPhoneNumberVerified()) {
                "인증된 보호자 연락처입니다."
            } else {
                null
            }
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val phoneNumberError = guardianPhoneNumberError(phoneNumber)
    val smsPhoneNumberError = if (smsPhoneNumber.isBlank()) {
        null
    } else {
        guardianPhoneNumberError(smsPhoneNumber)
    }
    val normalizedPhoneNumber = normalizeGuardianPhoneNumber(phoneNumber)

    fun verifyCredential(
        credential: PhoneAuthCredential,
        verifiedPhoneNumber: String
    ) {
        val auth = guardianPhoneAuth
        if (auth == null) {
            isBusy = false
            errorMessage = "Firebase 전화번호 인증을 사용할 수 없습니다."
            return
        }

        isBusy = true
        statusMessage = null
        errorMessage = null
        auth.signInWithCredential(credential)
            .addOnCompleteListener { result ->
                isBusy = false
                if (result.isSuccessful) {
                    auth.signOut()
                    val savedPhoneNumber = normalizeGuardianPhoneNumber(verifiedPhoneNumber)
                    guardianPreferences.saveVerifiedPhoneNumber(savedPhoneNumber)
                    phoneNumber = savedPhoneNumber
                    if (smsPhoneNumber.isBlank()) {
                        smsPhoneNumber = savedPhoneNumber
                    }
                    verificationCode = ""
                    verificationId = null
                    verificationPhoneNumber = null
                    resendToken = null
                    isCodeSent = false
                    statusMessage = "보호자 휴대폰 번호 인증이 완료되었습니다."
                    errorMessage = null
                } else {
                    errorMessage = result.exception?.localizedMessage
                        ?: "인증번호를 확인하지 못했습니다."
                }
            }
    }

    fun sendVerificationCode(forceResend: Boolean = false) {
        val auth = guardianPhoneAuth
        if (auth == null) {
            errorMessage = "Firebase 전화번호 인증을 사용할 수 없습니다."
            return
        }
        if (activity == null) {
            errorMessage = "전화번호 인증을 시작할 화면 정보를 찾지 못했습니다."
            return
        }
        if (phoneNumberError != null) {
            errorMessage = phoneNumberError
            return
        }

        val phoneNumberToVerify = normalizedPhoneNumber
        val firebasePhoneNumber = phoneNumberToVerify.toFirebasePhoneNumber()
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                verifyCredential(credential, phoneNumberToVerify)
            }

            override fun onVerificationFailed(exception: FirebaseException) {
                isBusy = false
                errorMessage = exception.localizedMessage ?: "인증번호를 보내지 못했습니다."
            }

            override fun onCodeSent(
                sentVerificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = sentVerificationId
                verificationPhoneNumber = phoneNumberToVerify
                resendToken = token
                isCodeSent = true
                isBusy = false
                statusMessage = "인증번호를 보냈습니다. 보호자에게 받은 인증번호를 입력해 주세요."
                errorMessage = null
            }
        }

        val optionsBuilder = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(firebasePhoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        if (forceResend) {
            resendToken?.let(optionsBuilder::setForceResendingToken)
        }

        isBusy = true
        statusMessage = null
        errorMessage = null
        PhoneAuthProvider.verifyPhoneNumber(optionsBuilder.build())
    }

    fun confirmVerificationCode() {
        val sentVerificationId = verificationId
        val sentPhoneNumber = verificationPhoneNumber
        val code = verificationCode.trim()
        when {
            sentVerificationId == null || sentPhoneNumber == null -> {
                errorMessage = "먼저 인증번호를 받아주세요."
            }
            sentPhoneNumber != normalizedPhoneNumber -> {
                errorMessage = "인증번호를 받은 휴대폰 번호와 현재 입력한 번호가 다릅니다."
            }
            code.isBlank() -> {
                errorMessage = "인증번호를 입력해 주세요."
            }
            else -> {
                verifyCredential(
                    credential = PhoneAuthProvider.getCredential(sentVerificationId, code),
                    verifiedPhoneNumber = sentPhoneNumber
                )
            }
        }
    }

    GuardianSettingsScreen(
        phoneNumber = phoneNumber,
        verificationCode = verificationCode,
        smsPhoneNumber = smsPhoneNumber,
        phoneNumberError = phoneNumberError,
        smsPhoneNumberError = smsPhoneNumberError,
        isCodeSent = isCodeSent,
        isBusy = isBusy,
        statusMessage = statusMessage,
        errorMessage = errorMessage,
        onPhoneNumberChanged = {
            phoneNumber = it
            verificationCode = ""
            verificationId = null
            verificationPhoneNumber = null
            resendToken = null
            isCodeSent = false
            statusMessage = null
            errorMessage = null
        },
        onVerificationCodeChanged = {
            verificationCode = it.filter(Char::isDigit).take(6)
            errorMessage = null
        },
        onSmsPhoneNumberChanged = {
            smsPhoneNumber = it
            statusMessage = null
            errorMessage = null
        },
        onSaveSmsPhoneNumberClick = {
            val normalizedSmsPhoneNumber = normalizeGuardianPhoneNumber(smsPhoneNumber)
            if (smsPhoneNumberError != null) {
                errorMessage = smsPhoneNumberError
            } else {
                guardianPreferences.saveSmsPhoneNumber(normalizedSmsPhoneNumber)
                smsPhoneNumber = normalizedSmsPhoneNumber
                statusMessage = "SOS 문자 수신 번호를 저장했습니다."
                errorMessage = null
            }
        },
        onSendCodeClick = { sendVerificationCode(false) },
        onResendCodeClick = { sendVerificationCode(true) },
        onVerifyCodeClick = ::confirmVerificationCode,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianSettingsScreen(
    phoneNumber: String,
    verificationCode: String,
    smsPhoneNumber: String,
    phoneNumberError: String?,
    smsPhoneNumberError: String?,
    isCodeSent: Boolean,
    isBusy: Boolean,
    statusMessage: String?,
    errorMessage: String?,
    onPhoneNumberChanged: (String) -> Unit,
    onVerificationCodeChanged: (String) -> Unit,
    onSmsPhoneNumberChanged: (String) -> Unit,
    onSaveSmsPhoneNumberClick: () -> Unit,
    onSendCodeClick: () -> Unit,
    onResendCodeClick: () -> Unit,
    onVerifyCodeClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("보호자 설정") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "뒤로 가기"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "SOS 문자를 받을 보호자의 휴대폰 번호를 인증합니다.",
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = phoneNumber,
                onValueChange = onPhoneNumberChanged,
                label = { Text("보호자 휴대폰 번호") },
                placeholder = { Text("01012345678") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = phoneNumber.isNotBlank() && phoneNumberError != null,
                supportingText = {
                    if (phoneNumber.isNotBlank() && phoneNumberError != null) {
                        Text(phoneNumberError)
                    }
                },
                singleLine = true
            )
            if (isCodeSent) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = verificationCode,
                    onValueChange = onVerificationCodeChanged,
                    label = { Text("인증번호") },
                    placeholder = { Text("6자리 숫자") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    onClick = onVerifyCodeClick
                ) {
                    Text(if (isBusy) "인증 중..." else "인증 후 저장")
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy && phoneNumberError == null,
                    onClick = onResendCodeClick
                ) {
                    Text("인증번호 다시 받기")
                }
            } else {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy && phoneNumberError == null,
                    onClick = onSendCodeClick
                ) {
                    Text(if (isBusy) "전송 중..." else "인증번호 받기")
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = smsPhoneNumber,
                onValueChange = onSmsPhoneNumberChanged,
                label = { Text("SOS 문자 수신 번호") },
                placeholder = { Text("01012345678") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = smsPhoneNumber.isNotBlank() && smsPhoneNumberError != null,
                supportingText = {
                    if (smsPhoneNumber.isNotBlank() && smsPhoneNumberError != null) {
                        Text(smsPhoneNumberError)
                    } else {
                        Text("비워두면 인증한 번호로 SOS 문자를 보냅니다.")
                    }
                },
                singleLine = true
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy && smsPhoneNumberError == null,
                onClick = onSaveSmsPhoneNumberClick
            ) {
                Text("SOS 수신 번호 저장")
            }
            statusMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.primary)
            }
            errorMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun getGuardianPhoneAuth(context: Context): FirebaseAuth {
    val app = FirebaseApp.getApps(context)
        .firstOrNull { it.name == GUARDIAN_PHONE_AUTH_APP_NAME }
        ?: FirebaseApp.initializeApp(
            context,
            FirebaseApp.getInstance().options,
            GUARDIAN_PHONE_AUTH_APP_NAME
        )
    return FirebaseAuth.getInstance(app)
}

private tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

private fun String.toFirebasePhoneNumber(): String {
    return "+82${drop(1)}"
}
