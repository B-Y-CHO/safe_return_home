package com.bycho.safereturnhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisibleMapFacilitiesTest {
    private fun select(points: List<FacilityMapPoint>, limit: Int = 200) =
        selectVisibleMapFacilities(points, { it }, 0.0, 0.0, 10.0, 10.0, 1080, 1920, 48, limit)

    @Test
    fun thousandsOfFacilitiesStayWithinMarkerBudget() {
        val points = (0 until 100).flatMap { row ->
            (0 until 100).map { column -> FacilityMapPoint(row / 10.0, column / 10.0) }
        }
        val selected = select(points)
        assertTrue(selected.isNotEmpty())
        assertTrue(selected.size <= 200)
        assertTrue(selected.all { it in points })
    }

    @Test
    fun duplicateAndFacilitiesInTheSameCellShareOneMarker() {
        val point = FacilityMapPoint(5.3, 5.3)
        assertEquals(listOf(point), select(listOf(point, point, FacilityMapPoint(5.301, 5.301))))
    }

    @Test
    fun panningReplacesFacilitiesOutsideTheVisibleArea() {
        val first = FacilityMapPoint(1.0, 1.0)
        val second = FacilityMapPoint(21.0, 21.0)
        assertEquals(listOf(first), select(listOf(first, second)))
        assertEquals(listOf(second), selectVisibleMapFacilities(
            listOf(first, second), { it }, 20.0, 20.0, 30.0, 30.0, 1080, 1920, 48
        ))
    }

    @Test
    fun ignoresInvalidCoordinatesAndUnreadyViewport() {
        assertTrue(select(listOf(FacilityMapPoint(Double.NaN, 1.0))).isEmpty())
        assertTrue(selectVisibleMapFacilities(
            listOf(FacilityMapPoint(1.0, 1.0)), { it }, 0.0, 0.0, 10.0, 10.0, 0, 0, 48
        ).isEmpty())
        assertTrue(select(listOf(FacilityMapPoint(1.0, 1.0)), limit = 0).isEmpty())
    }
}
