package com.bycho.safereturnhome.ui.state

data class SignalPollingUiState(
    val isPolling: Boolean = false,
    val latestStatus: String? = null,
    val serverMessage: String? = null,
    val statusMessage: String = "서버 주소를 설정하면 안전 상태 수신을 시작합니다."
)
