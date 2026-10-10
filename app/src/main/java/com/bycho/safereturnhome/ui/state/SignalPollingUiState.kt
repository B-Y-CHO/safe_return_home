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
    val statusMessage: String = "Preparing event server connection."
)
