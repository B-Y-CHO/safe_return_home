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
    fun canUseCctvCoordinatesThatCoverGeneralRoute() {
        val coordinates = listOf(
            cctv(latitude = 36.10, longitude = 128.3045, address = "near-route-1"),
            cctv(latitude = 36.10, longitude = 128.3090, address = "near-route-2"),
            cctv(latitude = 36.10, longitude = 128.3135, address = "near-route-3")
        )

        val plan = CctvSafeRoutePlanner.plan(
            startLatitude = 36.10,
            startLongitude = 128.30,
            destinationLatitude = 36.10,
            destinationLongitude = 128.318,
            cctvCoordinates = coordinates,
            maxWaypointCount = 3
        )

        assertTrue(plan.waypoints.isNotEmpty())
        assertTrue(plan.estimatedCoverageRatio > 0.5)
    }

    @Test
    fun choosesSingleWaypointDetourWhenCctvIsNearButNotOnGeneralRoute() {
        val plan = CctvSafeRoutePlanner.plan(
            startLatitude = 36.10,
            startLongitude = 128.30,
            destinationLatitude = 36.10,
            destinationLongitude = 128.318,
            cctvCoordinates = listOf(
                cctv(latitude = 36.101, longitude = 128.3090, address = "valid-single-detour")
            ),
            maxWaypointCount = 3
        )

        assertEquals(1, plan.waypoints.size)
    }

    @Test
    fun choosesDetourWhenCctvCoordinatesFormContinuousSafeCorridor() {
        val coordinates = listOf(
            cctv(latitude = 36.102, longitude = 128.3045, address = "corridor-1"),
            cctv(latitude = 36.102, longitude = 128.3090, address = "corridor-2"),
            cctv(latitude = 36.102, longitude = 128.3135, address = "corridor-3")
        )

        val plan = CctvSafeRoutePlanner.plan(
            startLatitude = 36.10,
            startLongitude = 128.30,
            destinationLatitude = 36.10,
            destinationLongitude = 128.318,
            cctvCoordinates = coordinates,
            maxWaypointCount = 3
        )

        assertTrue(plan.waypoints.size >= 2)
        assertTrue(plan.estimatedCoverageRatio > 0.5)
    }

    @Test
    fun streetlightsReducePreferenceCostAsSupplementaryCoverage() {
        val cctvCoordinates = listOf(
            cctv(latitude = 36.10, longitude = 128.3045, address = "route-1"),
            cctv(latitude = 36.10, longitude = 128.3090, address = "route-2"),
            cctv(latitude = 36.10, longitude = 128.3135, address = "route-3")
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
                streetlight(latitude = 36.10, longitude = 128.3045, address = "light-1"),
                streetlight(latitude = 36.10, longitude = 128.3090, address = "light-2"),
                streetlight(latitude = 36.10, longitude = 128.3135, address = "light-3")
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
    fun ignoresRouteIneligibleCctvCoordinatesFromSupplementalData() {
        val plan = CctvSafeRoutePlanner.plan(
            startLatitude = 36.10,
            startLongitude = 128.30,
            destinationLatitude = 36.10,
            destinationLongitude = 128.318,
            cctvCoordinates = listOf(
                cctv(
                    latitude = 36.101,
                    longitude = 128.3090,
                    address = "대백타운아파트",
                    routeEligible = false
                )
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
        address: String,
        routeEligible: Boolean = true
    ): CctvCoordinate {
        return CctvCoordinate(
            latitude = latitude,
            longitude = longitude,
            cameraCount = 4,
            managementNumber = address,
            address = address,
            routeEligible = routeEligible
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
