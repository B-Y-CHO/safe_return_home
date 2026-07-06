package com.bycho.safereturnhome.data

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRerouteHelperTest {
    private val dangerZone = DangerZone(
        id = "danger-1",
        latitude = 36.14,
        longitude = 128.40,
        radiusMeters = 50.0,
        message = "test"
    )

    @Test
    fun detectsDangerZoneOnRouteSegment() {
        val route = listOf(
            RouteCoordinate(36.14, 128.399),
            RouteCoordinate(36.14, 128.401)
        )

        assertTrue(RouteRerouteHelper.routeIntersectsDangerZone(route, dangerZone))
        assertTrue(RouteRerouteHelper.minimumDistanceMeters(route, dangerZone) < 1.0)
    }

    @Test
    fun collisionBufferCoversSmallCoordinateError() {
        val pointAbout55MetersNorth = RouteCoordinate(
            latitude = dangerZone.latitude + 55.0 / 111_320.0,
            longitude = dangerZone.longitude
        )
        val route = listOf(pointAbout55MetersNorth)

        assertFalse(
            RouteRerouteHelper.routeIntersectsDangerZone(
                route = route,
                dangerZone = dangerZone,
                bufferMeters = 0.0
            )
        )
        assertTrue(RouteRerouteHelper.routeIntersectsDangerZone(route, dangerZone))
    }

    @Test
    fun createsCandidateRingsOutsideDangerRadius() {
        val candidates = RouteRerouteHelper.createDetourCandidates(dangerZone)
        val expectedDistances = listOf(100.0, 170.0, 250.0, 400.0)

        assertEquals(expectedDistances.size * 8, candidates.size)
        candidates.chunked(8).zip(expectedDistances).forEach { (ring, expectedDistance) ->
            ring.forEach { candidate ->
                val distanceMeters = RouteRerouteHelper.minimumDistanceMeters(
                    route = listOf(candidate),
                    dangerZone = dangerZone
                )
                assertTrue(distanceMeters > dangerZone.radiusMeters)
                assertTrue(abs(distanceMeters - expectedDistance) < 1.0)
            }
        }
    }
}
