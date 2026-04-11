package com.bycho.safereturnhome.ui.state

data class TripUiState(
    val statusLabel: String = "귀가가 아직 시작되지 않았습니다.",
    val etaLabel: String = "예상 도착 시간은 아직 없습니다.",
    val locationShareEnabled: Boolean = true,
    val sosReady: Boolean = true
)
