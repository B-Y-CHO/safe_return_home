package com.bycho.safereturnhome.ui.screen.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapRouteRequestTest {
    @Test
    fun recreatedNavigationMapDoesNotReplayCompletedRequest() {
        assertFalse(shouldStartMapRouteRequest(false, true, 1, 0, true))
    }

    @Test
    fun nextRouteWaitsUntilItsNewMapIsReady() {
        assertFalse(shouldStartMapRouteRequest(true, false, 2, 0, true))
        assertTrue(shouldStartMapRouteRequest(true, true, 2, 0, true))
        assertFalse(shouldStartMapRouteRequest(true, true, 2, 2, true))
    }

    @Test
    fun callbacksFromDisposedOrReplacedRequestsAreIgnored() {
        assertFalse(isMapRouteRequestActive(true, true, 1, 1, true))
        assertFalse(isMapRouteRequestActive(false, true, 1, 2, true))
        assertFalse(isMapRouteRequestActive(false, true, 2, 2, false))
        assertTrue(isMapRouteRequestActive(false, true, 2, 2, true))
    }

    @Test
    fun finishedOrTimedOutRequestCannotRestoreLoadingState() {
        assertFalse(isMapRouteRequestActive(false, false, 2, 2, true))
    }
}
