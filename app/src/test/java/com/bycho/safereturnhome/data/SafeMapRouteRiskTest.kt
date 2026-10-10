package com.bycho.safereturnhome.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SafeMapRouteRiskTest {
    private val colors = listOf("ffffb2", "fee88b", "fed165", "fdb751", "fd9b43",
        "fa7a35", "f45629", "ea3420", "d31a23", "bd0026")
    private val bounds = SafeMapBounds(36.1, 128.4, 36.102, 128.403)

    @Test fun legendUsesNumericGradesAndAcceptsActualWmsRounding() {
        val legend = SafeMapLegend.fromResponse(legendResponse())!!
        assertEquals(10, legend.gradeOf(0x99bd0026.toInt()))
        assertEquals(2, legend.gradeOf(0x99fde88a.toInt()))
        assertEquals(7, legend.gradeOf(0x99f3572a.toInt()))
        assertEquals(0, legend.gradeOf(0x01ff0000))
        assertEquals(0, legend.gradeOf(0xffffffff.toInt()))
        assertEquals(0, legend.gradeOf(0xff0000ff.toInt()))
        // A real blended edge colour between grades remains unknown.
        assertEquals(0, legend.gradeOf(0x83ffef96.toInt()))
    }

    @Test fun incompleteOrErrorLegendCannotBeUsedForScoring() {
        assertNull(SafeMapLegend.fromResponse(legendResponse(9)))
        assertNull(SafeMapLegend.fromResponse("<ServiceException>Invalid key</ServiceException>"))
        assertNull(SafeMapLegend.fromResponse(legendResponse().replace("\"00\"", "\"30\"")))
    }

    @Test fun rasterMapsNorthToTopAndEastToRight() {
        val raster = raster { x, y -> if (x < 100 && y < 100) 2 else 9 }
        assertEquals(2, raster.gradeAt(point(20, 20)))
        assertEquals(9, raster.gradeAt(point(180, 20)))
        assertEquals(9, raster.gradeAt(point(20, 180)))
        assertEquals(0, raster.gradeAt(RouteCoordinate(36.2, 128.4)))
    }

    @Test fun unknownAndTransparentRoadsAreNotMeasuredAsSafe() {
        val exposure = raster { _, _ -> 0 }.measure(listOf(point(10, 100), point(190, 100)))
        assertEquals(0.0, exposure.knownMeters, 0.001)
        assertEquals(exposure.measuredMeters, exposure.unknownMeters, 0.001)
        assertEquals(exposure.measuredMeters, exposure.conservativeGradeMeters, 0.001)
    }

    @Test fun nearestClippedRoadWinsInsteadOfHigherGradeParallelRoad() {
        val raster = raster { _, y -> when (y) { 100 -> 3; 106 -> 10; else -> 0 } }
        assertEquals(3, raster.gradeAt(point(100, 101)))
        assertEquals(0, raster.gradeAt(point(100, 140)))
    }

    @Test fun exposureFollowsActualCurvedPolylineInsteadOfEndpointShortcut() {
        val raster = raster { x, y -> if (x in 30..170 && y in 85..115) 10 else 1 }
        val from = point(10, 100)
        val to = point(190, 100)
        val direct = raster.measure(listOf(from, to))
        val curved = raster.measure(listOf(from, point(10, 30), point(190, 30), to))
        assertTrue(direct.highGradeMeters > 100)
        assertEquals(0.0, curved.highGradeMeters, 0.01)
        assertTrue(curved.measuredMeters > direct.measuredMeters)
        assertTrue(curved.weightedGradeMeters < direct.weightedGradeMeters)
    }

    @Test fun exposureAccumulatesMetersRatherThanNumberOfVertices() {
        val raster = raster { _, _ -> 8 }
        val from = point(10, 100)
        val to = point(190, 100)
        val whole = raster.measure(listOf(from, to))
        val subdivided = raster.measure((0..10).map { index -> point(10 + index * 18, 100) })
        assertEquals(whole.measuredMeters, subdivided.measuredMeters, 0.01)
        assertEquals(whole.highGradeMeters, subdivided.highGradeMeters, 0.01)
        assertEquals(whole.weightedGradeMeters, subdivided.weightedGradeMeters, 0.01)
    }

    @Test fun lowerGradeExposureWinsDespiteHigherFacilityCost() {
        assertEquals(1, chooseSafeMapRoute(listOf(
            choice(1000, 1000.0, 1000.0, 500.0),
            choice(1200, 4000.0, 1200.0, 100.0)
        )))
    }

    @Test fun facilityCoverageBreaksSmallExposureTies() {
        assertEquals(1, chooseSafeMapRoute(listOf(
            choice(1000, 3000.0, 1000.0, 100.0),
            choice(1100, 1500.0, 1100.0, 120.0)
        )))
    }

    @Test fun missingDataOrInsufficientCoverageKeepsOriginalRoute() {
        val preferred = choice(1000, 2500.0, 1000.0, 400.0)
        assertEquals(0, chooseSafeMapRoute(listOf(preferred.copy(exposure = null),
            choice(1100, 1000.0, 1100.0, 0.0))))
        assertEquals(0, chooseSafeMapRoute(listOf(preferred,
            choice(1100, 1000.0, 500.0, 0.0))))
        assertEquals(0, chooseSafeMapRoute(listOf(
            choice(1000, 2500.0, 0.0, 0.0), choice(1100, 1000.0, 0.0, 0.0))))
    }

    @Test fun unclassifiedStretchCannotMakeAlternativeLookSafer() {
        assertEquals(0, chooseSafeMapRoute(listOf(
            choice(1000, 2500.0, 950.0, 150.0),
            choice(1000, 1000.0, 900.0, 130.0)
        )))
    }

    @Test fun endpointInsideHighGradeAreaIsAllowedRatherThanBanningAllRoutes() {
        assertEquals(1, chooseSafeMapRoute(listOf(
            choice(1000, 2000.0, 1000.0, 500.0),
            choice(1200, 2500.0, 1200.0, 300.0)
        )))
    }

    @Test fun detourHasThirtyPercentLimitAndSmallRouteAllowance() {
        assertTrue(isSafeRouteDetourAllowed(1000, 1300))
        assertFalse(isSafeRouteDetourAllowed(1000, 1301))
        assertTrue(isSafeRouteDetourAllowed(100, 200))
        assertFalse(isSafeRouteDetourAllowed(100, 201))
        assertFalse(isSafeRouteDetourAllowed(null, 1200))
    }

    @Test fun boundsCoverAllCandidatesAndRejectUnusableResolution() {
        val first = listOf(point(10, 100), point(190, 100))
        val second = listOf(point(10, 30), point(190, 30))
        val area = SafeMapBounds.forRoutes(listOf(first, second))!!
        assertTrue(area.south < first.first().latitude)
        assertTrue(area.north > second.first().latitude)
        assertTrue(area.west < first.first().longitude)
        assertTrue(area.east > first.last().longitude)
        assertNull(SafeMapBounds.forRoutes(listOf(listOf(RouteCoordinate(36.0, 128.0), RouteCoordinate(37.0, 129.0)))))
        assertNull(SafeMapBounds.forRoutes(listOf(listOf(RouteCoordinate(Double.NaN, 128.0)))))
        assertNull(SafeMapBounds.forRoutes(emptyList()))
    }

    private fun point(x: Int, y: Int) = RouteCoordinate(
        bounds.north - (y + 0.5) / 200 * (bounds.north - bounds.south),
        bounds.west + (x + 0.5) / 200 * (bounds.east - bounds.west)
    )

    private fun raster(grade: (Int, Int) -> Int) = SafeMapRiskRaster(bounds, 200, 200,
        ByteArray(200 * 200) { index -> grade(index % 200, index / 200).toByte() })

    private fun choice(distance: Int, cost: Double, known: Double, weighted: Double) =
        SafeMapRouteChoice(distance, cost, SafeMapRouteExposure(distance.toDouble(), known, 0.0, weighted))

    private fun legendResponse(count: Int = 10): String {
        // Grade 10 can precede grade 2 in the real response; never use array order as rank.
        val items = JSONArray()
        (1..count).sortedBy { it.toString() }.forEach { grade ->
            items.put(JSONObject().put("LAYER", SafeMapLegend.LAYER).put("STYLE", SafeMapLegend.STYLE)
                .put("LGD_NO", grade.toString()).put("IMAGE", "#${colors[grade - 1]}"))
        }
        return JSONObject().put("header", JSONObject().put("resultCode", "00"))
            .put("body", JSONObject().put("items", JSONObject().put("item", items))).toString()
    }
}
