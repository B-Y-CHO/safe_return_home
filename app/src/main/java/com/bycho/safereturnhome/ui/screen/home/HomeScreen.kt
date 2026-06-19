package com.bycho.safereturnhome.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bycho.safereturnhome.ui.state.HomeUiState
import com.bycho.safereturnhome.ui.state.SignalPollingUiState
import com.bycho.safereturnhome.ui.viewmodel.HomeViewModel
import com.bycho.safereturnhome.data.GuardianPreferences
import com.bycho.safereturnhome.data.RecentDestinationPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val SHOW_SERVER_CONTROLS = false

@Composable
fun HomeRoute(
    onStartTripClick: () -> Unit,
    onGuardianSettingsClick: () -> Unit,
    onServerSettingsClick: () -> Unit,
    signalPollingUiState: SignalPollingUiState,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val guardianPreferences = remember(context) { GuardianPreferences(context) }
    val recentDestinationPreferences = remember(context) { RecentDestinationPreferences(context) }
    var guardianRegistered by remember {
        mutableStateOf(guardianPreferences.getPhoneNumber().isNotBlank())
    }
    var lastDestinationLabel by remember {
        mutableStateOf(
            recentDestinationPreferences.getRecentDestination()?.label()
                ?: "최근 목적지가 없습니다."
        )
    }
    DisposableEffect(lifecycleOwner, guardianPreferences, recentDestinationPreferences) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                guardianRegistered = guardianPreferences.getPhoneNumber().isNotBlank()
                lastDestinationLabel = recentDestinationPreferences
                    .getRecentDestination()
                    ?.label()
                    ?: "최근 목적지가 없습니다."
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    HomeScreen(
        uiState = uiState.copy(
            guardianRegistered = guardianRegistered,
            lastDestinationLabel = lastDestinationLabel
        ),
        signalPollingUiState = signalPollingUiState,
        onStartTripClick = onStartTripClick,
        onGuardianSettingsClick = onGuardianSettingsClick,
        onServerSettingsClick = onServerSettingsClick
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    signalPollingUiState: SignalPollingUiState,
    onStartTripClick: () -> Unit,
    onGuardianSettingsClick: () -> Unit,
    onServerSettingsClick: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "안심 귀가",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "목적지를 설정하고 안전한 귀가 안내를 시작하세요.",
                style = MaterialTheme.typography.bodyLarge
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "보호자 등록: ${if (uiState.guardianRegistered) "완료" else "미등록"}")
                    Text(text = uiState.lastDestinationLabel)
                }
            }
            if (SHOW_SERVER_CONTROLS) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = "AI 서버 안전 상태")
                        Text(text = signalPollingUiState.statusMessage)
                        signalPollingUiState.latestStatus?.let {
                            Text(text = "현재 상태: $it")
                        }
                        signalPollingUiState.serverMessage?.let {
                            Text(text = it)
                        }
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onStartTripClick
                ) {
                    Text("귀가 시작")
                }
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onGuardianSettingsClick
            ) {
                Text("보호자 설정")
            }
            if (SHOW_SERVER_CONTROLS) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onServerSettingsClick
                ) {
                    Text("서버 설정")
                }
            }
        }
    }
}
