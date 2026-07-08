package com.bycho.safereturnhome.ui.state

import com.bycho.safereturnhome.data.DangerZone

data class SignalPollingUiState(
    val isPolling: Boolean = false,
    val isConnected: Boolean = false,
    val latestStatus: String? = null,
    val serverMessage: String? = null,
    val dangerZones: List<DangerZone> = emptyList(),
    val latestDangerZone: DangerZone? = null,
    val dangerZoneEventVersion: Long = 0L,
    val lastEventReceivedAtMillis: Long? = null,
    val escortStatusMessage: String? = null,
    val statusMessage: String = "이벤트 서버 연결을 준비하고 있습니다."
)
