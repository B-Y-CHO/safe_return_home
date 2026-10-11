package com.bycho.safereturnhome.data

import org.junit.Assert.*
import org.junit.Test

class SafeMapCandidatePlannerTest {
    private val bounds = SafeMapBounds(36.10, 128.30, 36.112, 128.318)
    private val start = point(40, 200)
    private val end = point(360, 200)
    private val route = listOf(start, end)

    @Test fun classifiedLowGradeRoadsGenerateDetoursWithoutCctv() {
        val raster = corridorRaster()
        val plans = SafeMapCandidatePlanner.plan(route, raster)
        assertTrue("A low-grade road corridor must produce a candidate", plans.isNotEmpty())
        val baseline = raster.measure(route)
        val best = plans.minOf { raster.measure(listOf(start) + it + end).conservativeGradeMeters }
        assertTrue("Suggested waypoints should lower estimated grade exposure", best < baseline.conservativeGradeMeters)
        for (plan in plans) {
            assertTrue(plan.size in 1..3)
            assertTrue(plan.all { raster.gradeAt(it) in 1..6 })
        }
    }

    @Test fun transparentMapSpaceCannotGenerateWaypoints() {
        assertTrue(SafeMapCandidatePlanner.plan(route, raster { _, _ -> 0 }).isEmpty())
    }

    @Test fun lowGradePixelsCannotOverrideInsufficientBaselineData() {
        val sparse = raster { x, y ->
            when {
                y == 145 -> 1
                y == 200 && x in 40..100 -> 10
                else -> 0
            }
        }
        assertTrue(SafeMapCandidatePlanner.plan(route, sparse).isEmpty())
    }

    @Test fun uniformlyLowGradeMapDoesNotCreateGratuitousDetours() {
        assertTrue(SafeMapCandidatePlanner.plan(route, raster { _, _ -> 1 }).isEmpty())
    }

    @Test fun roadOutsideCandidateCorridorIsNotUsed() {
        val distant = raster { _, y -> when (y) { 200 -> 10; 40 -> 1; else -> 0 } }
        assertTrue(SafeMapCandidatePlanner.plan(route, distant).isEmpty())
    }

    @Test fun waypointLimitAndInvalidCoordinatesAreRespected() {
        assertTrue(SafeMapCandidatePlanner.plan(route, corridorRaster(), 0).isEmpty())
        assertTrue(SafeMapCandidatePlanner.plan(route, corridorRaster(), 1).all { it.size <= 1 })
        assertTrue(SafeMapCandidatePlanner.plan(listOf(start), corridorRaster()).isEmpty())
        assertTrue(SafeMapCandidatePlanner.plan(listOf(start, RouteCoordinate(Double.NaN, 128.3)),
            corridorRaster()).isEmpty())
    }

    @Test fun expandedBoundsIncludeNearbyDetourRoads() {
        val normal = SafeMapBounds.forRoutes(listOf(route))!!
        val expanded = SafeMapBounds.forRoutes(listOf(route), SafeMapCandidatePlanner.corridorMeters(route) + 60)!!
        assertTrue(expanded.contains(normal))
        assertTrue(expanded.north > normal.north)
        assertTrue(expanded.south < normal.south)
        assertNull(SafeMapBounds.forRoutes(listOf(route), Double.NaN))
    }

    private fun corridorRaster() = raster { x, y -> when {
        y in 197..203 && x in 40..360 -> if (x in 100..300) 10 else 2
        y in 143..147 && x in 60..340 -> 2
        y in 253..257 && x in 60..340 -> 2
        (x in 58..62 || x in 338..342) && y in 145..255 -> 2
        else -> 0
    } }

    private fun point(x: Int, y: Int) = RouteCoordinate(
        bounds.north - (y + 0.5) / 400 * (bounds.north - bounds.south),
        bounds.west + (x + 0.5) / 400 * (bounds.east - bounds.west)
    )

    private fun raster(grade: (Int, Int) -> Int) = SafeMapRiskRaster(bounds, 400, 400,
        ByteArray(400 * 400) { index -> grade(index % 400, index / 400).toByte() })
}
