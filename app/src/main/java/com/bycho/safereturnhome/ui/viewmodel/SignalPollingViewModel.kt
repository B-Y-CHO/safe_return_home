package com.bycho.safereturnhome.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.bycho.safereturnhome.data.ServerPreferences
import com.bycho.safereturnhome.network.RaspberryPiHttpClient
import com.bycho.safereturnhome.ui.state.SignalPollingUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class SignalPollingViewModel(application: Application) : AndroidViewModel(application) {
    private val serverPreferences = ServerPreferences(application)
    private val httpClient = RaspberryPiHttpClient()
    private val _uiState = MutableStateFlow(SignalPollingUiState())
    val uiState: StateFlow<SignalPollingUiState> = _uiState.asStateFlow()

    suspend fun pollSignals() {
        while (currentCoroutineContext().isActive) {
            pollSignal()
            delay(POLLING_INTERVAL_MILLIS)
        }
    }

    private suspend fun pollSignal() {
        val serverAddress = serverPreferences.getServerAddress()
        if (serverAddress.isBlank()) {
            _uiState.value = SignalPollingUiState()
            return
        }

        val result = withContext(Dispatchers.IO) {
            httpClient.getServerStatus(serverAddress)
        }
        _uiState.value = if (result.errorMessage == null) {
            SignalPollingUiState(
                isPolling = true,
                latestStatus = result.status,
                serverMessage = result.message,
                statusMessage = "AI 서버의 안전 상태를 1초마다 확인하고 있습니다."
            )
        } else {
            SignalPollingUiState(
                isPolling = true,
                latestStatus = _uiState.value.latestStatus,
                serverMessage = _uiState.value.serverMessage,
                statusMessage = "안전 상태 조회 실패: ${result.errorMessage}"
            )
        }
    }

    private companion object {
        const val POLLING_INTERVAL_MILLIS = 1_000L
    }
}
