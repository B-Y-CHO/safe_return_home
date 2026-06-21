package com.bycho.safereturnhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CctvSafeRoutePlannerTest {
    @Test
    fun choosesDirectRouteWhenNoCctvCoordinatesExist() {
        val plan = CctvSafeRoutePlanner.plan(
            startLatitude = 36.10,
            startLongitude = 128.30,
            destinationLatitude = 36.10,
            destinationLongitude = 128.318,
            cctvCoordinates = emptyList(),
            maxWaypointCount = 3
        )

        assertTrue(plan.waypoints.isEmpty())
    }

    @Test
    fun choosesCctvWaypointsWhenCoveredDetourHasLowerPreferenceCost() {
        val coordinates = listOf(
            cctv(latitude = 36.102, longitude = 128.3045, address = "offset-1"),
            cctv(latitude = 36.102, longitude = 128.3090, address = "offset-2"),
            cctv(latitude = 36.102, longitude = 128.3135, address = "offset-3")
        )

        val plan = CctvSafeRoutePlanner.plan(
            startLatitude = 36.10,
            startLongitude = 128.30,
            destinationLatitude = 36.10,
            destinationLongitude = 128.318,
            cctvCoordinates = coordinates,
            maxWaypointCount = 3
        )

        assertEquals(3, plan.waypoints.size)
        assertTrue(plan.estimatedCoverageRatio > 0.5)
    }

    @Test
    fun streetlightsReducePreferenceCostAsSupplementaryCoverage() {
        val cctvCoordinates = listOf(
            cctv(latitude = 36.102, longitude = 128.3045, address = "offset-1"),
            cctv(latitude = 36.102, longitude = 128.3090, address = "offset-2"),
            cctv(latitude = 36.102, longitude = 128.3135, address = "offset-3")
        )
        val planWithoutStreetlights = CctvSafeRoutePlanner.plan(
            startLatitude = 36.10,
            startLongitude = 128.30,
            destinationLatitude = 36.10,
            destinationLongitude = 128.318,
            cctvCoordinates = cctvCoordinates,
            maxWaypointCount = 3
        )
        val planWithStreetlights = CctvSafeRoutePlanner.plan(
            startLatitude = 36.10,
            startLongitude = 128.30,
            destinationLatitude = 36.10,
            destinationLongitude = 128.318,
            cctvCoordinates = cctvCoordinates,
            streetlightCoordinates = listOf(
                streetlight(latitude = 36.102, longitude = 128.3045, address = "light-1"),
                streetlight(latitude = 36.102, longitude = 128.3090, address = "light-2"),
                streetlight(latitude = 36.102, longitude = 128.3135, address = "light-3")
            ),
            maxWaypointCount = 3
        )

        assertTrue(planWithStreetlights.estimatedCost < planWithoutStreetlights.estimatedCost)
        assertTrue(
            planWithStreetlights.estimatedStreetlightCoverageRatio >
                planWithoutStreetlights.estimatedStreetlightCoverageRatio
        )
    }

    @Test
    fun ignoresIsolatedSideWaypointThatWouldOnlyCreateASpur() {
        val plan = CctvSafeRoutePlanner.plan(
            startLatitude = 36.10,
            startLongitude = 128.30,
            destinationLatitude = 36.10,
            destinationLongitude = 128.318,
            cctvCoordinates = listOf(
                cctv(latitude = 36.102, longitude = 128.3090, address = "isolated-side-cctv")
            ),
            streetlightCoordinates = listOf(
                streetlight(latitude = 36.102, longitude = 128.3090, address = "isolated-light")
            ),
            maxWaypointCount = 3
        )

        assertTrue(plan.waypoints.isEmpty())
    }

    @Test
    fun countsOnlyCctvCoordinatesRelevantToCurrentRoute() {
        val count = CctvSafeRoutePlanner.countRelevantCoordinates(
            startLatitude = 36.10,
            startLongitude = 128.30,
            destinationLatitude = 36.10,
            destinationLongitude = 128.318,
            cctvCoordinates = listOf(
                cctv(latitude = 36.101, longitude = 128.309, address = "near-route"),
                cctv(latitude = 36.14, longitude = 128.309, address = "far-from-route")
            )
        )

        assertEquals(1, count)
    }

    private fun cctv(
        latitude: Double,
        longitude: Double,
        address: String
    ): CctvCoordinate {
        return CctvCoordinate(
            latitude = latitude,
            longitude = longitude,
            cameraCount = 4,
            managementNumber = address,
            address = address
        )
    }

    private fun streetlight(
        latitude: Double,
        longitude: Double,
        address: String
    ): StreetlightCoordinate {
        return StreetlightCoordinate(
            latitude = latitude,
            longitude = longitude,
            lightCount = 4,
            address = address,
            fixtureType = "LED"
        )
    }
}
