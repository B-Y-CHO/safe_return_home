package com.bycho.safereturnhome.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.bycho.safereturnhome.data.DangerZone
import com.bycho.safereturnhome.data.ServerPreferences
import com.bycho.safereturnhome.network.RaspberryWebSocketEvent
import com.bycho.safereturnhome.network.RaspberryWebSocketManager
import com.bycho.safereturnhome.network.parseDangerZoneMessage
import com.bycho.safereturnhome.network.parseServerStatusResponse
import com.bycho.safereturnhome.ui.state.SignalPollingUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest

class SignalPollingViewModel(application: Application) : AndroidViewModel(application) {
    private val serverPreferences = ServerPreferences(application)
    private val webSocketManager = RaspberryWebSocketManager()
    private val _uiState = MutableStateFlow(SignalPollingUiState())
    val uiState: StateFlow<SignalPollingUiState> = _uiState.asStateFlow()

    suspend fun pollSignals() {
        serverPreferences.observeServerAddress().collectLatest { configuredAddress ->
            val serverAddress = configuredAddress.ifBlank {
                RaspberryWebSocketManager.DEFAULT_SERVER_ADDRESS
            }
            _uiState.value = _uiState.value.copy(
                isPolling = true,
                statusMessage = "라즈베리파이 WebSocket 서버에 연결하는 중입니다."
            )
            webSocketManager.observe(serverAddress).collectLatest { event ->
                when (event) {
                    is RaspberryWebSocketEvent.Connected -> {
                        _uiState.value = _uiState.value.copy(
                            isPolling = true,
                            statusMessage = "WebSocket 연결 성공. 위험지점 정보를 수신 중입니다."
                        )
                    }
                    is RaspberryWebSocketEvent.MessageReceived -> {
                        val dangerZoneResult = runCatching { parseDangerZoneMessage(event.text) }
                        val dangerZone = dangerZoneResult.getOrNull()
                        if (dangerZone != null) {
                            handleDangerZone(dangerZone)
                        } else if (dangerZoneResult.isFailure) {
                            Log.e(
                                LOG_TAG,
                                "위험지점 JSON 파싱 실패: " +
                                    dangerZoneResult.exceptionOrNull()?.message,
                                dangerZoneResult.exceptionOrNull()
                            )
                            _uiState.value = _uiState.value.copy(
                                isPolling = true,
                                statusMessage = "위험지점 데이터 오류: " +
                                    (dangerZoneResult.exceptionOrNull()?.message
                                        ?: "알 수 없는 오류")
                            )
                        } else {
                            val result = runCatching {
                                parseServerStatusResponse(event.text)
                            }.getOrNull()
                            if (result != null && result.errorMessage == null) {
                                _uiState.value = _uiState.value.copy(
                                    isPolling = true,
                                    latestStatus = result.status,
                                    latestDangerZone = if (result.status.equals("NORMAL", ignoreCase = true)) null else _uiState.value.latestDangerZone,
                                    serverMessage = result.message,
                                    statusMessage = if (result.status.equals("NORMAL", ignoreCase = true)) "정상 상태: 위험지점이 해제되었습니다." else "AI 서버와 연결되었습니다. 실시간으로 안전 상태를 수신 중입니다."
                                )
                            } else {
                                val errorMsg = result?.errorMessage ?: "데이터 파싱 에러"
                                _uiState.value = _uiState.value.copy(
                                    isPolling = true,
                                    statusMessage = "안전 상태 데이터 오류: $errorMsg"
                                )
                            }
                        }
                    }
                    is RaspberryWebSocketEvent.Failed -> {
                        _uiState.value = _uiState.value.copy(
                            isPolling = false,
                            statusMessage = "연결 실패: ${event.message}"
                        )
                    }
                    is RaspberryWebSocketEvent.Closed -> {
                        _uiState.value = _uiState.value.copy(
                            isPolling = false,
                            statusMessage = "연결이 종료되었습니다. (사유: ${event.reason})"
                        )
                    }
                }
            }
        }
    }

    fun handleDangerZone(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        id: String,
        message: String
    ) {
        handleDangerZone(
            DangerZone(
                id = id,
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters,
                message = message
            )
        )
    }

    fun handleDangerZone(dangerZone: DangerZone) {
        Log.i(
            LOG_TAG,
            "위험지점 수신: id=${dangerZone.id}, lat=${dangerZone.latitude}, " +
                "lon=${dangerZone.longitude}, radius=${dangerZone.radiusMeters}"
        )
        _uiState.value = _uiState.value.copy(
            isPolling = true,
            latestDangerZone = dangerZone,
            dangerZoneEventVersion = _uiState.value.dangerZoneEventVersion + 1L,
            serverMessage = dangerZone.message,
            statusMessage = "위험지점 정보를 수신했습니다."
        )
    }

    private companion object {
        const val LOG_TAG = "SignalPollingViewModel"
    }
}
