package com.bycho.safereturnhome.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bycho.safereturnhome.ui.state.HomeUiState
import com.bycho.safereturnhome.ui.viewmodel.HomeViewModel

@Composable
fun HomeRoute(
    onStartTripClick: () -> Unit,
    onSosClick: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    HomeScreen(
        uiState = uiState,
        onStartTripClick = onStartTripClick,
        onSosClick = onSosClick
    )
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onStartTripClick: () -> Unit,
    onSosClick: () -> Unit
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
                text = "귀가 시작, 위치 공유, SOS 흐름을 검증하기 위한 MVP 화면입니다.",
                style = MaterialTheme.typography.bodyLarge
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "보호자 등록: ${if (uiState.guardianRegistered) "완료" else "미등록"}")
                    Text(text = "활성 귀가: ${if (uiState.activeTrip) "진행 중" else "없음"}")
                    Text(text = uiState.lastDestinationLabel)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onStartTripClick
                ) {
                    Text("귀가 시작")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onSosClick
                ) {
                    Text("SOS")
                }
            }
        }
    }
}
