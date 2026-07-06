package com.bycho.safereturnhome.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bycho.safereturnhome.ui.state.HomeUiState
import com.bycho.safereturnhome.ui.state.SignalPollingUiState
import com.bycho.safereturnhome.ui.viewmodel.HomeViewModel
import com.bycho.safereturnhome.ui.viewmodel.MapViewModel
import com.bycho.safereturnhome.data.GuardianPreferences
import com.bycho.safereturnhome.data.RecentDestinationPreferences
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val SHOW_SERVER_CONTROLS = true

@Composable
fun HomeRoute(
    onStartTripClick: () -> Unit,
    onSosClick: () -> Unit,
    onGuardianSettingsClick: () -> Unit,
    onServerSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    signalPollingUiState: SignalPollingUiState,
    viewModel: HomeViewModel = viewModel(),
    mapViewModel: MapViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val mapUiState by mapViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val guardianPreferences = remember(context) { GuardianPreferences(context) }
    val recentDestinationPreferences = remember(context) { RecentDestinationPreferences(context) }
    var guardianRegistered by remember {
        mutableStateOf(guardianPreferences.isPhoneNumberVerified())
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
                guardianRegistered = guardianPreferences.isPhoneNumberVerified()
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
        isNavigationActive = mapUiState.isNavigationActive,
        signalPollingUiState = signalPollingUiState,
        onStartTripClick = onStartTripClick,
        onSosClick = onSosClick,
        onGuardianSettingsClick = onGuardianSettingsClick,
        onServerSettingsClick = onServerSettingsClick,
        onLogoutClick = onLogoutClick
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    isNavigationActive: Boolean,
    signalPollingUiState: SignalPollingUiState,
    onStartTripClick: () -> Unit,
    onSosClick: () -> Unit,
    onGuardianSettingsClick: () -> Unit,
    onServerSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "구미 안심귀가",
                style = MaterialTheme.typography.headlineMedium
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "목적지를 정하고 안전한 경로로 이동하세요.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "CCTV와 가로등 정보를 참고하고, 위급할 때는 SOS 문자를 보낼 수 있습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        onClick = onStartTripClick
                    ) {
                        Text(if (isNavigationActive) "안내 계속하기" else "귀가 시작하기")
                    }
                }
            }

            HomeStatusCard(
                uiState = uiState,
                isNavigationActive = isNavigationActive
            )

            if (!uiState.guardianRegistered) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "보호자 인증이 필요합니다.",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "SOS 문자를 보내려면 보호자 휴대폰 인증과 수신 번호 저장을 먼저 완료하세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onGuardianSettingsClick
                        ) {
                            Text("보호자 설정하기")
                        }
                    }
                }
            }

            FeatureSummary()

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
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onLogoutClick
            ) {
                Text("로그아웃")
            }
        }
    }
}

@Composable
private fun HomeStatusCard(
    uiState: HomeUiState,
    isNavigationActive: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "오늘의 준비 상태",
                style = MaterialTheme.typography.titleMedium
            )
            StatusLine(
                label = "보호자 인증",
                value = if (uiState.guardianRegistered) "완료" else "필요",
                isPositive = uiState.guardianRegistered
            )
            StatusLine(
                label = "SOS 문자",
                value = if (uiState.guardianRegistered) "사용 가능" else "보호자 설정 필요",
                isPositive = uiState.guardianRegistered
            )
            StatusLine(
                label = "귀가 안내",
                value = if (isNavigationActive) "진행 중" else "대기",
                isPositive = isNavigationActive
            )
            StatusLine(
                label = "최근 목적지",
                value = uiState.lastDestinationLabel,
                isPositive = uiState.lastDestinationLabel != "최근 목적지가 없습니다."
            )
        }
    }
}

@Composable
private fun StatusLine(
    label: String,
    value: String,
    isPositive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            color = if (isPositive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun FeatureSummary() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "안전 기능",
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FeatureItem(
                modifier = Modifier.weight(1f),
                title = "안전 경로",
                description = "CCTV와 가로등 참고"
            )
            FeatureItem(
                modifier = Modifier.weight(1f),
                title = "SOS",
                description = "3초 길게 눌러 문자"
            )
        }
        FeatureItem(
            modifier = Modifier.fillMaxWidth(),
            title = "경로 이탈 대응",
            description = "이동 중 경로를 벗어나면 재탐색을 안내합니다."
        )
    }
}

@Composable
private fun FeatureItem(
    modifier: Modifier,
    title: String,
    description: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
