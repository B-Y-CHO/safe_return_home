package com.bycho.safereturnhome.ui.screen.guardian

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

@Composable
fun GuardianSettingsRoute(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val guardianPreferences = remember(context) { GuardianPreferences(context) }
    var phoneNumber by remember { mutableStateOf(guardianPreferences.getPhoneNumber()) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    val phoneNumberError = guardianPhoneNumberError(phoneNumber)

    GuardianSettingsScreen(
        phoneNumber = phoneNumber,
        phoneNumberError = phoneNumberError,
        savedMessage = savedMessage,
        onPhoneNumberChanged = {
            phoneNumber = it
            savedMessage = null
        },
        onSaveClick = {
            phoneNumber = normalizeGuardianPhoneNumber(phoneNumber)
            guardianPreferences.savePhoneNumber(phoneNumber)
            savedMessage = "보호자 연락처를 저장했습니다."
        },
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianSettingsScreen(
    phoneNumber: String,
    phoneNumberError: String?,
    savedMessage: String?,
    onPhoneNumberChanged: (String) -> Unit,
    onSaveClick: () -> Unit,
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
                text = "SOS 문자를 받을 보호자의 연락처를 등록합니다.",
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
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = phoneNumberError == null,
                onClick = onSaveClick
            ) {
                Text("보호자 연락처 저장")
            }
            savedMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
