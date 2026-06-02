package com.bycho.safereturnhome.ui.screen.server

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bycho.safereturnhome.R
import com.bycho.safereturnhome.data.ServerPreferences
import com.bycho.safereturnhome.data.normalizeServerAddress
import com.bycho.safereturnhome.data.serverAddressError
import com.bycho.safereturnhome.network.RaspberryPiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ServerSettingsRoute(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val serverPreferences = remember(context) { ServerPreferences(context) }
    val httpClient = remember { RaspberryPiHttpClient() }
    val coroutineScope = rememberCoroutineScope()
    var serverAddress by remember { mutableStateOf(serverPreferences.getServerAddress()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }
    val addressError = serverAddressError(serverAddress)

    ServerSettingsScreen(
        serverAddress = serverAddress,
        serverAddressError = addressError,
        statusMessage = statusMessage,
        isTestingConnection = isTestingConnection,
        onServerAddressChanged = {
            serverAddress = it
            statusMessage = null
        },
        onSaveClick = {
            serverAddress = normalizeServerAddress(serverAddress)
            serverPreferences.saveServerAddress(serverAddress)
            statusMessage = "서버 주소를 저장했습니다."
        },
        onTestConnectionClick = {
            serverAddress = normalizeServerAddress(serverAddress)
            serverPreferences.saveServerAddress(serverAddress)
            val addressToTest = serverAddress
            isTestingConnection = true
            statusMessage = null
            coroutineScope.launch {
                val result = withContext(Dispatchers.IO) {
                    httpClient.checkConnection(addressToTest)
                }
                isTestingConnection = false
                statusMessage = if (result.isSuccessful) {
                    "연결에 성공했습니다. ${result.message}"
                } else {
                    "연결에 실패했습니다. ${result.message}"
                }
            }
        },
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(
    serverAddress: String,
    serverAddressError: String?,
    statusMessage: String?,
    isTestingConnection: Boolean,
    onServerAddressChanged: (String) -> Unit,
    onSaveClick: () -> Unit,
    onTestConnectionClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("서버 설정") },
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
                text = "라즈베리파이 HTTP 서버의 기본 주소를 입력하세요.",
                style = MaterialTheme.typography.bodyLarge
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = serverAddress,
                onValueChange = onServerAddressChanged,
                label = { Text("서버 주소") },
                placeholder = { Text("http://192.168.0.10:5000") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = serverAddress.isNotBlank() && serverAddressError != null,
                supportingText = {
                    if (serverAddress.isNotBlank() && serverAddressError != null) {
                        Text(serverAddressError)
                    }
                },
                singleLine = true
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = serverAddressError == null && !isTestingConnection,
                onClick = onSaveClick
            ) {
                Text("서버 주소 저장")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = serverAddressError == null && !isTestingConnection,
                onClick = onTestConnectionClick
            ) {
                Text(if (isTestingConnection) "연결 확인 중..." else "연결 테스트")
            }
            statusMessage?.let {
                Text(text = it, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
