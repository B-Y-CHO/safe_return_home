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
import com.bycho.safereturnhome.data.DEFAULT_EVENT_SERVER_ADDRESS
import com.bycho.safereturnhome.data.ServerPreferences
import com.bycho.safereturnhome.data.normalizeServerAddress
import com.bycho.safereturnhome.data.serverAddressError
import com.bycho.safereturnhome.network.EventHttpClient
import kotlinx.coroutines.launch

@Composable
fun ServerSettingsRoute(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { ServerPreferences(context) }
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(preferences.getServerAddress()) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val error = serverAddressError(address)

    ServerSettingsScreen(
        serverAddress = address,
        serverAddressError = error,
        statusMessage = status,
        isTestingConnection = testing,
        onServerAddressChanged = { address = it; status = null },
        onUseEmulatorDefault = { address = DEFAULT_EVENT_SERVER_ADDRESS; status = null },
        onSaveClick = {
            address = normalizeServerAddress(address)
            preferences.saveServerAddress(address)
            status = "서버 주소를 저장했습니다."
        },
        onTestConnectionClick = {
            address = normalizeServerAddress(address)
            if (address.isBlank()) {
                status = "서버 주소를 입력해 주세요."
            } else {
                preferences.saveServerAddress(address)
                testing = true
                scope.launch {
                    status = runCatching { EventHttpClient(address).checkServer() }
                        .fold(
                            onSuccess = { "연결 성공: FastAPI 서버가 응답했습니다." },
                            onFailure = { "연결 실패: ${it.message}" }
                        )
                    testing = false
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
    onUseEmulatorDefault: () -> Unit,
    onSaveClick: () -> Unit,
    onTestConnectionClick: () -> Unit,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("이벤트 서버 설정") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(painterResource(R.drawable.ic_arrow_back), "뒤로 가기")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("FastAPI 서버의 HTTP 기본 주소를 입력하세요. WebSocket /ws 주소는 자동 생성됩니다.")
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = serverAddress,
                onValueChange = onServerAddressChanged,
                label = { Text("서버 주소") },
                placeholder = { Text(DEFAULT_EVENT_SERVER_ADDRESS) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = serverAddress.isNotBlank() && serverAddressError != null,
                supportingText = {
                    if (serverAddress.isBlank()) Text("주소가 비어 있으면 서버에 연결하지 않습니다.")
                    else serverAddressError?.let { Text(it) }
                },
                singleLine = true
            )
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onUseEmulatorDefault
            ) {
                Text("에뮬레이터 기본 주소 사용")
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = serverAddressError == null && !isTestingConnection,
                onClick = onSaveClick
            ) { Text("서버 주소 저장") }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = serverAddressError == null && !isTestingConnection,
                onClick = onTestConnectionClick
            ) { Text(if (isTestingConnection) "연결 확인 중…" else "HTTP 연결 테스트") }
            statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Text("실제 스마트폰: PC와 같은 Wi-Fi에서 http://PC_IP:8000", style = MaterialTheme.typography.bodySmall)
        }
    }
}
