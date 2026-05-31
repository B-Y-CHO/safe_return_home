package com.bycho.safereturnhome.ui.state

data class DestinationSearchResult(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

data class MapUiState(
    val currentLocationLabel: String = "현재 위치를 아직 불러오지 않았습니다.",
    val destinationLabel: String = "목적지가 아직 선택되지 않았습니다.",
    val routeSummary: String = "위치 연결 후 경로 안내를 준비합니다.",
    val mapStatusLabel: String = "TMap 지도를 준비하는 중입니다.",
    val hasApiKey: Boolean = false,
    val isMapReady: Boolean = false,
    val isLocationPermissionGranted: Boolean = false,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val currentLocationAccuracyMeters: Float? = null,
    val currentLocationBearingDegrees: Float = 0f,
    val gpsSignalLabel: String = "GPS signal: Waiting for location",
    val destinationQuery: String = "",
    val destinationSearchResults: List<DestinationSearchResult> = emptyList(),
    val isDestinationSearchInProgress: Boolean = false,
    val destinationSearchMessage: String? = null,
    val selectedDestination: DestinationSearchResult? = null,
    val isRouteSearchInProgress: Boolean = false,
    val routeSearchMessage: String? = null,
    val initialRouteDistanceMeters: Int? = null,
    val remainingRouteDistanceMeters: Int? = null,
    val routeProgress: Float = 0f
)
