package com.bycho.safereturnhome.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bycho.safereturnhome.data.DangerZone
import com.bycho.safereturnhome.data.ServerPreferences
import com.bycho.safereturnhome.network.DangerEventUpdate
import com.bycho.safereturnhome.network.FastApiDangerEventSource
import com.bycho.safereturnhome.ui.state.SignalPollingUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SignalPollingViewModel(application: Application) : AndroidViewModel(application) {
    private val serverPreferences = ServerPreferences(application)
    private val _uiState = MutableStateFlow(SignalPollingUiState())
    val uiState: StateFlow<SignalPollingUiState> = _uiState.asStateFlow()

    suspend fun pollSignals() {
        serverPreferences.observeServerAddress().collectLatest { serverAddress ->
            if (serverAddress.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    isPolling = false,
                    isConnected = false,
                    statusMessage = "서버 주소를 설정해 주세요."
                )
                return@collectLatest
            }

            val eventSource = FastApiDangerEventSource(serverAddress)
            _uiState.value = _uiState.value.copy(
                isPolling = true,
                isConnected = false,
                statusMessage = "FastAPI 이벤트 서버에 연결하는 중입니다."
            )

            runCatching { eventSource.fetchDangerZones() }
                .onSuccess { zones ->
                    _uiState.value = _uiState.value.copy(
                        dangerZones = zones,
                        latestDangerZone = zones.lastOrNull(),
                        dangerZoneEventVersion = if (zones.isEmpty()) {
                            _uiState.value.dangerZoneEventVersion
                        } else {
                            _uiState.value.dangerZoneEventVersion + 1L
                        }
                    )
                }
                .onFailure { error ->
                    Log.w(LOG_TAG, "Initial danger-zone fetch failed", error)
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "위험구역 초기 조회 실패: ${error.message}"
                    )
                }

            while (currentCoroutineContext().isActive) {
                eventSource.observeDangerZones().collect { update ->
                    when (update) {
                        is DangerEventUpdate.Connected -> _uiState.value = _uiState.value.copy(
                            isPolling = true,
                            isConnected = true,
                            statusMessage = "연결됨"
                        )
                        is DangerEventUpdate.DangerZoneCreated -> handleDangerZone(update.dangerZone)
                        is DangerEventUpdate.Failed -> {
                            Log.e(LOG_TAG, update.message, update.cause)
                            _uiState.value = _uiState.value.copy(
                                isPolling = false,
                                isConnected = false,
                                statusMessage = "연결 실패: ${update.message}"
                            )
                        }
                        is DangerEventUpdate.Closed -> _uiState.value = _uiState.value.copy(
                            isPolling = false,
                            isConnected = false,
                            statusMessage = "연결 종료: ${update.reason}"
                        )
                    }
                }
                if (currentCoroutineContext().isActive) {
                    _uiState.value = _uiState.value.copy(statusMessage = "3초 후 다시 연결합니다.")
                    delay(RECONNECT_DELAY_MILLIS)
                }
            }
        }
    }

    fun requestUavEscort(latitude: Double, longitude: Double) {
        val address = serverPreferences.getServerAddress()
        if (address.isBlank()) {
            _uiState.value = _uiState.value.copy(escortStatusMessage = "서버 주소를 설정해 주세요.")
            return
        }
        _uiState.value = _uiState.value.copy(escortStatusMessage = "UAV 동행을 요청하는 중입니다.")
        viewModelScope.launch {
            runCatching {
                FastApiDangerEventSource(address).requestUavEscort(latitude, longitude)
            }.onSuccess { response ->
                _uiState.value = _uiState.value.copy(
                    escortStatusMessage = "UAV 동행 요청 완료 · ${response.robotId} 출동 중"
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    escortStatusMessage = "UAV 동행 요청 실패: ${error.message}"
                )
            }
        }
    }

    fun handleDangerZone(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
        id: String,
        message: String
    ) = handleDangerZone(DangerZone(id, latitude, longitude, radiusMeters, message))

    fun handleDangerZone(dangerZone: DangerZone) {
        val zones = _uiState.value.dangerZones.filterNot { it.id == dangerZone.id } + dangerZone
        _uiState.value = _uiState.value.copy(
            isPolling = true,
            isConnected = true,
            dangerZones = zones,
            latestDangerZone = dangerZone,
            dangerZoneEventVersion = _uiState.value.dangerZoneEventVersion + 1L,
            lastEventReceivedAtMillis = System.currentTimeMillis(),
            serverMessage = dangerZone.message,
            statusMessage = "연결됨"
        )
    }

    private companion object {
        const val LOG_TAG = "DangerEventViewModel"
        const val RECONNECT_DELAY_MILLIS = 3_000L
    }
}
