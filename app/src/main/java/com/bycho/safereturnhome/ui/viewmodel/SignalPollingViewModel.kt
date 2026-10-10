package com.bycho.safereturnhome.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.bycho.safereturnhome.data.DangerZone
import com.bycho.safereturnhome.data.ServerPreferences
import com.bycho.safereturnhome.network.DangerEventUpdate
import com.bycho.safereturnhome.network.FastApiDangerEventSource
import com.bycho.safereturnhome.ui.state.SignalPollingUiState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive

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
                    statusMessage = "Server address is empty."
                )
                return@collectLatest
            }

            val eventSource = FastApiDangerEventSource(serverAddress)
            _uiState.value = _uiState.value.copy(
                isPolling = true,
                isConnected = false,
                statusMessage = "Connecting to FastAPI event server."
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
                        statusMessage = "Initial danger-zone fetch failed: ${error.message}"
                    )
                }

            while (currentCoroutineContext().isActive) {
                eventSource.observeDangerZones().collect { update ->
                    when (update) {
                        is DangerEventUpdate.Connected -> _uiState.value = _uiState.value.copy(
                            isPolling = true,
                            isConnected = true,
                            statusMessage = "Connected."
                        )
                        is DangerEventUpdate.DangerZoneCreated ->
                            handleDangerZone(update.dangerZone)
                        DangerEventUpdate.DangerZonesCleared ->
                            clearDangerZones()
                        is DangerEventUpdate.Failed -> {
                            Log.e(LOG_TAG, update.message, update.cause)
                            _uiState.value = _uiState.value.copy(
                                isPolling = false,
                                isConnected = false,
                                statusMessage = "Connection failed: ${update.message}"
                            )
                        }
                        is DangerEventUpdate.Closed -> _uiState.value = _uiState.value.copy(
                            isPolling = false,
                            isConnected = false,
                            statusMessage = "Connection closed: ${update.reason}"
                        )
                    }
                }
                if (currentCoroutineContext().isActive) {
                    _uiState.value = _uiState.value.copy(
                        statusMessage = "Reconnecting in 3 seconds."
                    )
                    delay(RECONNECT_DELAY_MILLIS)
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
    ) = handleDangerZone(DangerZone(id, latitude, longitude, radiusMeters, message))

    fun handleDangerZone(dangerZone: DangerZone) {
        _uiState.value = _uiState.value.copy(
            isPolling = true,
            isConnected = true,
            dangerZones = listOf(dangerZone),
            latestDangerZone = dangerZone,
            dangerZoneEventVersion = _uiState.value.dangerZoneEventVersion + 1L,
            lastEventReceivedAtMillis = System.currentTimeMillis(),
            serverMessage = dangerZone.message,
            statusMessage = "Connected."
        )
    }

    fun clearDangerZones() {
        _uiState.value = _uiState.value.copy(
            dangerZones = emptyList(),
            latestDangerZone = null,
            dangerZoneEventVersion = _uiState.value.dangerZoneEventVersion + 1L,
            serverMessage = null,
            statusMessage = "Connected."
        )
    }

    private companion object {
        const val LOG_TAG = "DangerEventViewModel"
        const val RECONNECT_DELAY_MILLIS = 3_000L
    }
}
