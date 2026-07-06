package com.bycho.safereturnhome.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import com.bycho.safereturnhome.BuildConfig
import com.bycho.safereturnhome.service.NavigationService
import com.bycho.safereturnhome.ui.state.DestinationSearchResult
import com.bycho.safereturnhome.ui.state.MapUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MapViewModel(application: Application) : AndroidViewModel(application) {
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

    fun onCurrentLocationLoaded(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        bearingDegrees: Float
    ) {
        val destination = _uiState.value.selectedDestination
        _uiState.value = _uiState.value.copy(
            currentLatitude = latitude,
            currentLongitude = longitude,
            currentLocationAccuracyMeters = accuracyMeters,
            currentLocationBearingDegrees = bearingDegrees,
            gpsSignalLabel = gpsSignalLabel(accuracyMeters),
            currentLocationLabel = "현재 위치: %.5f, %.5f".format(latitude, longitude),
            mapStatusLabel = "현재 위치를 중심으로 지도를 이동했습니다.",
            routeSummary = destination?.let {
                destinationDistanceLabel(latitude, longitude, it)
            } ?: _uiState.value.routeSummary
        )
    }

    fun onCurrentLocationUnavailable(reason: String) {
        _uiState.value = _uiState.value.copy(
            currentLocationLabel = "현재 위치를 가져올 수 없습니다: $reason",
            gpsSignalLabel = "GPS signal: Unavailable"
        )
    }

    fun onDestinationQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(destinationQuery = query)
    }

    fun onDestinationSearchStarted() {
        _uiState.value = _uiState.value.copy(
            isDestinationSearchInProgress = true,
            destinationSearchMessage = null
        )
    }

    fun onDestinationSearchCompleted(results: List<DestinationSearchResult>) {
        _uiState.value = _uiState.value.copy(
            destinationSearchResults = results,
            isDestinationSearchInProgress = false,
            destinationSearchMessage = if (results.isEmpty()) "검색 결과가 없습니다." else null
        )
    }

    fun onDestinationSearchFailed(reason: String) {
        _uiState.value = _uiState.value.copy(
            destinationSearchResults = emptyList(),
            isDestinationSearchInProgress = false,
            destinationSearchMessage = "목적지 검색에 실패했습니다: $reason"
        )
    }

    fun onDestinationSelected(destination: DestinationSearchResult) {
        val currentLatitude = _uiState.value.currentLatitude
        val currentLongitude = _uiState.value.currentLongitude
        _uiState.value = _uiState.value.copy(
            selectedDestination = destination,
            destinationSearchResults = emptyList(),
            destinationLabel = "목적지: ${destination.name}",
            initialRouteDistanceMeters = null,
            remainingRouteDistanceMeters = null,
            routeProgress = 0f,
            routeSummary = if (currentLatitude != null && currentLongitude != null) {
                destinationDistanceLabel(currentLatitude, currentLongitude, destination)
            } else {
                "현재 위치를 확인하면 목적지까지의 거리를 표시합니다."
            }
        )
    }

    fun onRouteSearchStarted() {
        _uiState.value = _uiState.value.copy(
            isRouteSearchInProgress = true,
            routeSearchMessage = "경로를 검색하는 중입니다."
        )
    }

    fun onRouteSearchProgress(message: String) {
        _uiState.value = _uiState.value.copy(
            isRouteSearchInProgress = true,
            routeSearchMessage = message
        )
    }

    fun onRouteSearchCompleted(distanceMeters: Int) {
        val walkingMinutes = (distanceMeters / 80f).toInt().coerceAtLeast(1)
        val initialRouteDistanceMeters = _uiState.value.initialRouteDistanceMeters ?: distanceMeters
        val shouldStartNavigation = !_uiState.value.isNavigationActive
        val progress = calculateRouteProgress(initialRouteDistanceMeters, distanceMeters)
        _uiState.value = _uiState.value.copy(
            isRouteSearchInProgress = false,
            routeSearchMessage = "보행자 경로를 지도에 표시했습니다.",
            routeSummary = "남은 보행 경로: ${distanceMeters}m · 예상 도보 시간: 약 ${walkingMinutes}분",
            initialRouteDistanceMeters = initialRouteDistanceMeters,
            remainingRouteDistanceMeters = distanceMeters,
            routeProgress = progress,
            isNavigationActive = true
        )
        if (shouldStartNavigation) startNavigationService()
        updateServiceNotification(
            progress = (progress * 100).toInt(),
            remainingDistance = distanceMeters,
            etaMinutes = walkingMinutes
        )
    }

    fun onRouteSearchFailed(reason: String) {
        _uiState.value = _uiState.value.copy(
            isRouteSearchInProgress = false,
            routeSearchMessage = "보행자 경로를 불러오지 못했습니다: $reason"
        )
    }

    fun onRemainingRouteDistanceChanged(distanceMeters: Int) {
        val walkingMinutes = (distanceMeters / 80f).toInt().coerceAtLeast(1)
        val initialRouteDistanceMeters = _uiState.value.initialRouteDistanceMeters ?: distanceMeters
        val progress = calculateRouteProgress(initialRouteDistanceMeters, distanceMeters)
        _uiState.value = _uiState.value.copy(
            routeSummary = "남은 보행 경로: ${distanceMeters}m · 예상 도보 시간: 약 ${walkingMinutes}분",
            remainingRouteDistanceMeters = distanceMeters,
            routeProgress = progress
        )
        if (_uiState.value.isNavigationActive) {
            updateServiceNotification(
                progress = (progress * 100).toInt(),
                remainingDistance = distanceMeters,
                etaMinutes = walkingMinutes
            )
        }
    }

    fun onRouteRecalculationStarted() {
        _uiState.value = _uiState.value.copy(
            isRouteSearchInProgress = true,
            routeSearchMessage = "경로를 벗어났습니다. 보행자 경로를 다시 검색합니다."
        )
    }

    fun onHazardRerouteStarted(message: String) {
        _uiState.value = _uiState.value.copy(
            isRouteSearchInProgress = true,
            routeSearchMessage = message
        )
    }

    fun onHazardRerouteCompleted(distanceMeters: Int) {
        onRouteSearchCompleted(distanceMeters)
        val walkingMinutes = (distanceMeters / 80f).toInt().coerceAtLeast(1)
        _uiState.value = _uiState.value.copy(
            routeSearchMessage = "위험지점 감지로 우회 경로를 안내합니다.",
            routeSummary = "위험지점 우회 중 · 남은 경로: ${distanceMeters}m · 약 ${walkingMinutes}분"
        )
    }

    fun onHazardRerouteFailed(message: String) {
        _uiState.value = _uiState.value.copy(
            isRouteSearchInProgress = false,
            routeSearchMessage = message
        )
    }

    fun onDestinationArrived() {
        _uiState.value = _uiState.value.copy(
            isRouteSearchInProgress = false,
            routeSearchMessage = "목적지에 도착했습니다.",
            routeSummary = "남은 보행 경로: 0m · 예상 도보 시간: 0분",
            remainingRouteDistanceMeters = 0,
            routeProgress = 1f,
            isNavigationActive = false
        )
        stopNavigationService()
    }

    fun stopNavigation() {
        if (_uiState.value.isNavigationActive) {
            _uiState.value = _uiState.value.copy(isNavigationActive = false)
            stopNavigationService()
        }
    }

    private fun startNavigationService() {
        val application = getApplication<Application>()
        val intent = Intent(application, NavigationService::class.java).apply {
            action = NavigationService.ACTION_START
        }
        application.startForegroundService(intent)
    }

    private fun updateServiceNotification(progress: Int, remainingDistance: Int, etaMinutes: Int) {
        val application = getApplication<Application>()
        val intent = Intent(application, NavigationService::class.java).apply {
            action = NavigationService.ACTION_UPDATE
            putExtra(NavigationService.EXTRA_PROGRESS, progress)
            putExtra(NavigationService.EXTRA_REMAINING_DISTANCE, remainingDistance)
            putExtra(NavigationService.EXTRA_ETA_MINUTES, etaMinutes)
        }
        application.startService(intent)
    }

    private fun stopNavigationService() {
        val application = getApplication<Application>()
        val intent = Intent(application, NavigationService::class.java).apply {
            action = NavigationService.ACTION_STOP
        }
        application.startService(intent)
    }

    private fun calculateRouteProgress(initialDistanceMeters: Int, remainingDistanceMeters: Int): Float {
        if (initialDistanceMeters <= 0) return 0f
        return (1f - remainingDistanceMeters.toFloat() / initialDistanceMeters)
            .coerceIn(0f, 1f)
    }

    private fun destinationDistanceLabel(
        currentLatitude: Double,
        currentLongitude: Double,
        destination: DestinationSearchResult
    ): String {
        val distanceResult = FloatArray(1)
        Location.distanceBetween(
            currentLatitude,
            currentLongitude,
            destination.latitude,
            destination.longitude,
            distanceResult
        )
        val distanceMeters = distanceResult[0].toInt()
        val walkingMinutes = (distanceMeters / 80f).toInt().coerceAtLeast(1)
        return "직선 거리: ${distanceMeters}m · 예상 도보 시간: 약 ${walkingMinutes}분"
    }

    private fun gpsSignalLabel(accuracyMeters: Float): String {
        val quality = when {
            accuracyMeters <= 20f -> "Good"
            accuracyMeters <= 50f -> "Fair"
            else -> "Weak"
        }
        return "GPS signal: $quality (${accuracyMeters.toInt()} m)"
    }
}
