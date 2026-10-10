package com.bycho.safereturnhome.ui.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MapNavigationStateTest {
    @Test
    fun stoppingDuringRecalculationClearsStateForTheNextDestination() {
        val stopped = MapUiState(
            isNavigationActive = true,
            isRouteSearchInProgress = true,
            routeSearchMessage = "Preparing route",
            selectedDestination = DestinationSearchResult("Old destination", "", 36.1, 128.3),
            initialRouteDistanceMeters = 1000,
            remainingRouteDistanceMeters = 300,
            routeProgress = 0.7f,
            currentLatitude = 36.2,
            currentLongitude = 128.4,
            isMapReady = true
        ).withNavigationStopped()

        assertFalse(stopped.isNavigationActive)
        assertFalse(stopped.isRouteSearchInProgress)
        assertNull(stopped.routeSearchMessage)
        assertNull(stopped.selectedDestination)
        assertNull(stopped.initialRouteDistanceMeters)
        assertNull(stopped.remainingRouteDistanceMeters)
        assertEquals(0f, stopped.routeProgress)
        assertEquals(36.2, stopped.currentLatitude!!, 0.0)
    }

    @Test
    fun stoppingBeforeNavigationStartsAlsoClearsPendingSearch() {
        val stopped = MapUiState(isRouteSearchInProgress = true, isNavigationActive = false)
            .withNavigationStopped()
        assertFalse(stopped.isRouteSearchInProgress)
        assertEquals(stopped, stopped.withNavigationStopped())
    }
}
