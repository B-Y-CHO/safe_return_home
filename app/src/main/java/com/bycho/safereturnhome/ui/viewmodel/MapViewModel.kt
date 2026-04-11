package com.bycho.safereturnhome.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.bycho.safereturnhome.BuildConfig
import com.bycho.safereturnhome.ui.state.MapUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MapViewModel : ViewModel() {
    private val hasApiKey = BuildConfig.TMAP_API_KEY.isNotBlank()

    private val _uiState = MutableStateFlow(
        MapUiState(
            mapStatusLabel = if (hasApiKey) {
                "TMap API 키가 설정되었습니다. 지도를 불러오는 중입니다."
            } else {
                "TMap API 키가 없습니다. local.properties를 확인해 주세요."
            },
            hasApiKey = hasApiKey
        )
    )
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    fun onMapReady() {
        _uiState.value = _uiState.value.copy(
            mapStatusLabel = "지도가 준비되었습니다. 현재 위치를 확인하는 중입니다.",
            routeSummary = "현재 단계: 내 위치로 지도를 이동합니다.",
            isMapReady = true
        )
    }

    fun onApiKeyFailed(message: String?) {
        _uiState.value = _uiState.value.copy(
            mapStatusLabel = "TMap API 키 인증에 실패했습니다: ${message ?: "알 수 없는 오류"}",
            isMapReady = false
        )
    }

    fun onLocationPermissionResult(isGranted: Boolean) {
        _uiState.value = _uiState.value.copy(
            isLocationPermissionGranted = isGranted,
            currentLocationLabel = if (isGranted) {
                "위치 권한이 허용되었습니다. 현재 위치를 불러오는 중입니다."
            } else {
                "위치 권한이 거부되어 현재 위치를 불러올 수 없습니다."
            }
        )
    }

    fun onCurrentLocationLoaded(latitude: Double, longitude: Double) {
        _uiState.value = _uiState.value.copy(
            currentLatitude = latitude,
            currentLongitude = longitude,
            currentLocationLabel = "현재 위치: %.5f, %.5f".format(latitude, longitude),
            mapStatusLabel = "현재 위치를 중심으로 지도를 이동했습니다."
        )
    }

    fun onCurrentLocationUnavailable(reason: String) {
        _uiState.value = _uiState.value.copy(
            currentLocationLabel = "현재 위치를 가져올 수 없습니다: $reason"
        )
    }
}
