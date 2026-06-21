package com.bycho.safereturnhome.ui.state

data class HomeUiState(
    val guardianRegistered: Boolean = false,
    val lastDestinationLabel: String = "최근 목적지가 없습니다."
)
