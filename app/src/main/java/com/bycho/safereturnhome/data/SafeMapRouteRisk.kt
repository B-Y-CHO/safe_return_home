package com.bycho.safereturnhome.data

import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow

/** Bounds of the original, uncomposited EPSG:4326 WMS image. */
internal data class SafeMapBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    val heightMeters get() = (north - south) * 111_320.0
    val widthMeters get() = (east - west) * 111_320.0 * cos(Math.toRadians((south + north) / 2))

    fun contains(other: SafeMapBounds) = south <= other.south && west <= other.west &&
        north >= other.north && east >= other.east

    companion object {
        fun forRoutes(routes: List<List<RouteCoordinate>>, marginMeters: Double = 60.0): SafeMapBounds? {
            if (!marginMeters.isFinite() || marginMeters < 0.0) return null
            val points = routes.flatten()
            if (points.isEmpty() || points.any {
                    !it.latitude.isFinite() || !it.longitude.isFinite() ||
                        it.latitude !in -85.0..85.0 || it.longitude !in -180.0..180.0
                }) return null
            val latitude = points.map { it.latitude }.average()
            val latMargin = marginMeters / 111_320.0
            val lonMargin = latMargin / cos(Math.toRadians(latitude))
            // Stable bounds allow the preview and subsequent navigation search to share a raster.
            val step = 0.002
            val bounds = SafeMapBounds(
                floor((points.minOf { it.latitude } - latMargin) / step) * step,
                floor((points.minOf { it.longitude } - lonMargin) / step) * step,
                ceil((points.maxOf { it.latitude } + latMargin) / step) * step,
                ceil((points.maxOf { it.longitude } + lonMargin) / step) * step
            )
            // Beyond 8 m/pixel the clipped road colours become too imprecise for route selection.
            return bounds.takeIf { it.widthMeters in 1.0..8192.0 && it.heightMeters in 1.0..8192.0 }
        }
    }
}

internal class SafeMapLegend private constructor(private val colors: Map<Int, Int>) {
    fun gradeOf(argb: Int): Int {
        if ((argb ushr 24) < 64) return 0
        val red = argb ushr 16 and 255
        val green = argb ushr 8 and 255
        val blue = argb and 255
        var grade = 0
        var closest = 65 // Allow minor PNG/alpha rounding, not arbitrary similar colours.
        for ((rank, color) in colors) {
            val difference = (red - (color ushr 16 and 255)).let { it * it } +
                (green - (color ushr 8 and 255)).let { it * it } +
                (blue - (color and 255)).let { it * it }
            if (difference < closest) {
                closest = difference
                grade = rank
            }
        }
        return grade
    }

    companion object {
        const val LAYER = "A2SM_CRMNLHSPOT_TOT"
        const val STYLE = "A2SM_CrmnlHspot_Tot_Tot"

        fun fromResponse(response: String): SafeMapLegend? = runCatching {
            val root = JSONObject(response)
            require(root.getJSONObject("header").getString("resultCode") == "00")
            val items = root.getJSONObject("body").getJSONObject("items").getJSONArray("item")
            val colors = mutableMapOf<Int, Int>()
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                if (item.optString("LAYER") != LAYER || item.optString("STYLE") != STYLE) continue
                val grade = item.getString("LGD_NO").toInt()
                val hex = item.getString("IMAGE")
                require(grade in 1..10 && hex.matches(Regex("#[0-9a-fA-F]{6}")))
                require(colors.put(grade, hex.drop(1).toInt(16)) == null)
            }
            require(colors.keys == (1..10).toSet() && colors.values.toSet().size == 10)
            SafeMapLegend(colors)
        }.getOrNull()
    }
}

internal data class SafeMapRouteExposure(
    val measuredMeters: Double,
    val knownMeters: Double,
    val highGradeMeters: Double,
    val weightedGradeMeters: Double
) {
    val knownRatio get() = if (measuredMeters > 0) knownMeters / measuredMeters else 0.0
    val unknownMeters get() = (measuredMeters - knownMeters).coerceAtLeast(0.0)
    // Missing road data is never a zero-risk shortcut.
    val conservativeGradeMeters get() = weightedGradeMeters + unknownMeters
}

internal class SafeMapRiskRaster(
    val bounds: SafeMapBounds,
    private val width: Int,
    private val height: Int,
    private val grades: ByteArray
) {
    init {
        require(width > 0 && height > 0 && grades.size == width * height)
        require(bounds.widthMeters > 0 && bounds.heightMeters > 0)
    }

    /** Keep actual classified road pixels, rather than placing waypoints on transparent map space. */
    fun roadPoints(cellMeters: Double = 100.0): List<RouteCoordinate> {
        require(cellMeters.isFinite() && cellMeters > 0.0)
        val cellWidth = ceil(cellMeters / (bounds.widthMeters / width)).toInt().coerceAtLeast(1)
        val cellHeight = ceil(cellMeters / (bounds.heightMeters / height)).toInt().coerceAtLeast(1)
        val points = mutableListOf<RouteCoordinate>()
        for (top in 0 until height step cellHeight) {
            for (left in 0 until width step cellWidth) {
                val right = minOf(left + cellWidth, width)
                val bottom = minOf(top + cellHeight, height)
                var bestIndex = -1
                var bestGrade = 11
                var bestDistance = Double.POSITIVE_INFINITY
                val centerX = (left + right - 1) / 2.0
                val centerY = (top + bottom - 1) / 2.0
                for (y in top until bottom) {
                    for (x in left until right) {
                        val grade = grades[y * width + x].toInt()
                        if (grade !in 1..10 || grade > bestGrade) continue
                        val distance = (x - centerX).pow(2) + (y - centerY).pow(2)
                        if (grade < bestGrade || distance < bestDistance) {
                            bestIndex = y * width + x
                            bestGrade = grade
                            bestDistance = distance
                        }
                    }
                }
                if (bestIndex >= 0) points += RouteCoordinate(
                    bounds.north - ((bestIndex / width) + 0.5) / height * (bounds.north - bounds.south),
                    bounds.west + ((bestIndex % width) + 0.5) / width * (bounds.east - bounds.west)
                )
            }
        }
        return points
    }

    fun gradeAt(point: RouteCoordinate): Int {
        if (point.latitude !in bounds.south..bounds.north || point.longitude !in bounds.west..bounds.east) return 0
        val x = ((point.longitude - bounds.west) / (bounds.east - bounds.west) * width)
            .toInt().coerceIn(0, width - 1)
        val y = ((bounds.north - point.latitude) / (bounds.north - bounds.south) * height)
            .toInt().coerceIn(0, height - 1)
        val exact = grades[y * width + x].toInt()
        if (exact in 1..10) return exact
        // WMS roads are clipped lines. Resolve small map-provider offsets to the nearest line,
        // rather than taking the highest grade from a neighbouring parallel road.
        val dx = bounds.widthMeters / width
        val dy = bounds.heightMeters / height
        val rx = ceil(12.0 / dx).toInt().coerceAtMost(64)
        val ry = ceil(12.0 / dy).toInt().coerceAtMost(64)
        var nearest = 12.0
        var grade = 0
        for (py in (y - ry).coerceAtLeast(0)..(y + ry).coerceAtMost(height - 1)) {
            for (px in (x - rx).coerceAtLeast(0)..(x + rx).coerceAtMost(width - 1)) {
                val candidate = grades[py * width + px].toInt()
                if (candidate !in 1..10) continue
                val distance = hypot((px - x) * dx, (py - y) * dy)
                if (distance < nearest || (distance == nearest && candidate > grade)) {
                    nearest = distance
                    grade = candidate
                }
            }
        }
        return grade
    }

    fun measure(route: List<RouteCoordinate>, sampleIntervalMeters: Double = 10.0): SafeMapRouteExposure {
        require(sampleIntervalMeters.isFinite() && sampleIntervalMeters > 0.0)
        var measured = 0.0
        var known = 0.0
        var high = 0.0
        var weighted = 0.0
        for ((from, to) in route.zipWithNext()) {
            val distance = routePointDistance(from, to)
            if (!distance.isFinite() || distance <= 0) continue
            val count = ceil(distance / sampleIntervalMeters).toInt().coerceIn(1, 10_000)
            val stepMeters = distance / count
            for (index in 0 until count) {
                val fraction = (index + 0.5) / count
                val grade = gradeAt(RouteCoordinate(
                    from.latitude + (to.latitude - from.latitude) * fraction,
                    from.longitude + (to.longitude - from.longitude) * fraction
                ))
                measured += stepMeters
                if (grade == 0) continue
                known += stepMeters
                if (grade >= 7) high += stepMeters
                weighted += stepMeters * (grade / 10.0).pow(2)
            }
        }
        return SafeMapRouteExposure(measured, known, high, weighted)
    }
}

internal fun routePointDistance(from: RouteCoordinate, to: RouteCoordinate): Double = hypot(
    (to.latitude - from.latitude) * 111_320.0,
    (to.longitude - from.longitude) * 111_320.0 * cos(Math.toRadians((from.latitude + to.latitude) / 2))
)

internal data class SafeMapRouteChoice(
    val distanceMeters: Int,
    val facilityCost: Double,
    val exposure: SafeMapRouteExposure?
)

/** Index zero is the previously preferred CCTV route, also used when analysis is unavailable. */
internal fun chooseSafeMapRoute(candidates: List<SafeMapRouteChoice>): Int {
    if (candidates.size < 2) return 0
    val reference = candidates.first().exposure ?: return 0
    if (reference.knownRatio < 0.65) return 0
    val comparable = candidates.indices.filter { index ->
        val exposure = candidates[index].exposure
        exposure != null && exposure.knownRatio >= 0.65 &&
            exposure.knownRatio >= reference.knownRatio - 0.05
    }
    if (comparable.size < 2) return 0
    val leastExposure = comparable.minOf { candidates[it].exposure!!.conservativeGradeMeters }
    // CCTV/lighting only break ties within a small (25 m weighted) exposure difference.
    return comparable.filter { candidates[it].exposure!!.conservativeGradeMeters <= leastExposure + 25.0 }
        .minWithOrNull(compareBy<Int> { candidates[it].facilityCost }.thenBy { candidates[it].distanceMeters }) ?: 0
}

internal fun isSafeRouteDetourAllowed(generalMeters: Int?, candidateMeters: Int): Boolean {
    if (generalMeters == null || generalMeters <= 0 || candidateMeters <= 0) return false
    return candidateMeters <= generalMeters + maxOf(100, (generalMeters * 0.30).toInt())
}
