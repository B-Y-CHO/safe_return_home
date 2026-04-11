package com.bycho.safereturnhome.ui.screen.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bycho.safereturnhome.ui.state.TripUiState
import com.bycho.safereturnhome.ui.viewmodel.TripViewModel

@Composable
fun TripRoute(
    onBackClick: () -> Unit,
    onEndTripClick: () -> Unit,
    viewModel: TripViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    TripScreen(
        uiState = uiState,
        onBackClick = onBackClick,
        onEndTripClick = onEndTripClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripScreen(
    uiState: TripUiState,
    onBackClick: () -> Unit,
    onEndTripClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("귀가 진행") }
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
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = uiState.statusLabel, style = MaterialTheme.typography.titleMedium)
                    Text(text = uiState.etaLabel)
                    Text(text = "위치 공유: ${if (uiState.locationShareEnabled) "활성" else "비활성"}")
                    Text(text = "SOS 준비 상태: ${if (uiState.sosReady) "가능" else "불가"}")
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { }
            ) {
                Text("SOS 발동")
            }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onEndTripClick
            ) {
                Text("귀가 종료")
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBackClick
            ) {
                Text("뒤로 가기")
            }
        }
    }
}
