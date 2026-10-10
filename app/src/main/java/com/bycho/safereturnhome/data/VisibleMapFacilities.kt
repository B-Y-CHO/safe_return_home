package com.bycho.safereturnhome.data

import kotlin.math.floor
import kotlin.math.sqrt

data class FacilityMapPoint(val latitude: Double, val longitude: Double)

/** Picks one facility per screen cell and never returns more than [maxMarkers]. */
internal fun <T> selectVisibleMapFacilities(
    facilities: List<T>,
    pointOf: (T) -> FacilityMapPoint,
    south: Double,
    west: Double,
    north: Double,
    east: Double,
    widthPixels: Int,
    heightPixels: Int,
    spacingPixels: Int,
    maxMarkers: Int = 200
): List<T> {
    if (north <= south || east <= west || widthPixels <= 0 || heightPixels <= 0 ||
        spacingPixels <= 0 || maxMarkers <= 0
    ) return emptyList()
    val cellSize = maxOf(
        spacingPixels.toDouble(),
        sqrt(widthPixels.toDouble() * heightPixels / maxMarkers)
    )
    val columns = floor(widthPixels / cellSize).toInt().coerceIn(1, maxMarkers)
    val rows = floor(heightPixels / cellSize).toInt().coerceIn(1, maxMarkers / columns)
    val cells = LinkedHashMap<Pair<Int, Int>, T>()
    for (facility in facilities) {
        val point = pointOf(facility)
        if (!point.latitude.isFinite() || !point.longitude.isFinite() ||
            point.latitude !in south..north || point.longitude !in west..east
        ) continue
        val column = floor((point.longitude - west) / (east - west) * columns)
            .toInt().coerceIn(0, columns - 1)
        val row = floor((north - point.latitude) / (north - south) * rows)
            .toInt().coerceIn(0, rows - 1)
        cells.putIfAbsent(column to row, facility)
    }
    return cells.values.toList()
}
