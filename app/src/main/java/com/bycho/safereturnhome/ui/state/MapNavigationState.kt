package com.bycho.safereturnhome.ui.state

/** Called even when navigation ended while a route request was still running. */
internal fun MapUiState.withNavigationStopped(): MapUiState = copy(
    isNavigationActive = false,
    isRouteSearchInProgress = false,
    routeSearchMessage = null,
    initialRouteDistanceMeters = null,
    remainingRouteDistanceMeters = null,
    routeProgress = 0f,
    selectedDestination = null,
    destinationQuery = "",
    destinationSearchResults = emptyList(),
    isDestinationSearchInProgress = false,
    destinationSearchMessage = null,
    destinationLabel = "목적지가 아직 선택되지 않았습니다.",
    routeSummary = "목적지를 선택하면 경로 안내를 준비합니다."
)
