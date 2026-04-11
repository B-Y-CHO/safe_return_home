package com.bycho.safereturnhome.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.bycho.safereturnhome.ui.state.TripUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TripViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        TripUiState(
            statusLabel = "귀가 진행 중",
            etaLabel = "예상 도착 18분"
        )
    )
    val uiState: StateFlow<TripUiState> = _uiState.asStateFlow()
}
