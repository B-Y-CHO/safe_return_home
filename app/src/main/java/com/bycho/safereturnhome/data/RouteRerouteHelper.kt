package com.bycho.safereturnhome.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

object RouteRerouteHelper {
    const val DEFAULT_COLLISION_BUFFER_METERS = 10.0
    const val DEFAULT_DETOUR_EXTRA_OFFSET_METERS = 50.0

    fun routeIntersectsDangerZone(
        route: List<RouteCoordinate>,
        dangerZone: DangerZone,
        bufferMeters: Double = DEFAULT_COLLISION_BUFFER_METERS
    ): Boolean {
        if (route.isEmpty()) return false
        return minimumDistanceMeters(route, dangerZone) <=
            dangerZone.radiusMeters + bufferMeters.coerceAtLeast(0.0)
    }

    fun minimumDistanceMeters(
        route: List<RouteCoordinate>,
        dangerZone: DangerZone
    ): Double {
        if (route.isEmpty()) return Double.POSITIVE_INFINITY
        val origin = RouteCoordinate(dangerZone.latitude, dangerZone.longitude)
        val dangerPoint = FlatPoint(0.0, 0.0)
        val projectedRoute = route.map { coordinate -> project(coordinate, origin) }
        if (projectedRoute.size == 1) {
            return hypot(projectedRoute.first().x, projectedRoute.first().y)
        }
        return projectedRoute.zipWithNext().minOf { (start, end) ->
            distanceFromPointToSegment(dangerPoint, start, end)
        }
    }

    fun createDetourCandidates(
        dangerZone: DangerZone,
        extraOffsetMeters: Double = DEFAULT_DETOUR_EXTRA_OFFSET_METERS,
        includeDiagonals: Boolean = true
    ): List<RouteCoordinate> {
        val directions = buildList {
            add(Direction(north = 1.0, east = 0.0))
            add(Direction(north = -1.0, east = 0.0))
            add(Direction(north = 0.0, east = 1.0))
            add(Direction(north = 0.0, east = -1.0))
            if (includeDiagonals) {
                val diagonalComponent = 1.0 / sqrt(2.0)
                add(Direction(north = diagonalComponent, east = diagonalComponent))
                add(Direction(north = diagonalComponent, east = -diagonalComponent))
                add(Direction(north = -diagonalComponent, east = diagonalComponent))
                add(Direction(north = -diagonalComponent, east = -diagonalComponent))
            }
        }
        val longitudeMetersPerDegree = METERS_PER_LATITUDE_DEGREE *
            cos(dangerZone.latitude * PI / 180.0).coerceAtLeast(MIN_LONGITUDE_SCALE)
        val offsets = listOf(
            (dangerZone.radiusMeters + extraOffsetMeters).coerceAtLeast(1.0),
            (dangerZone.radiusMeters + 120.0).coerceAtLeast(1.0),
            (dangerZone.radiusMeters + 200.0).coerceAtLeast(1.0),
            (dangerZone.radiusMeters + 350.0).coerceAtLeast(1.0)
        )
        return offsets.flatMap { offset ->
            directions.map { direction ->
                RouteCoordinate(
                    latitude = dangerZone.latitude +
                        direction.north * offset / METERS_PER_LATITUDE_DEGREE,
                    longitude = dangerZone.longitude +
                        direction.east * offset / longitudeMetersPerDegree
                )
            }
        }
    }

    private fun project(coordinate: RouteCoordinate, origin: RouteCoordinate): FlatPoint {
        val averageLatitude = (coordinate.latitude + origin.latitude) / 2.0
        val longitudeMetersPerDegree = METERS_PER_LATITUDE_DEGREE *
            cos(averageLatitude * PI / 180.0).coerceAtLeast(MIN_LONGITUDE_SCALE)
        return FlatPoint(
            x = (coordinate.longitude - origin.longitude) * longitudeMetersPerDegree,
            y = (coordinate.latitude - origin.latitude) * METERS_PER_LATITUDE_DEGREE
        )
    }

    private fun distanceFromPointToSegment(
        point: FlatPoint,
        segmentStart: FlatPoint,
        segmentEnd: FlatPoint
    ): Double {
        val segmentX = segmentEnd.x - segmentStart.x
        val segmentY = segmentEnd.y - segmentStart.y
        val segmentLengthSquared = segmentX * segmentX + segmentY * segmentY
        if (segmentLengthSquared <= 0.0) {
            return hypot(point.x - segmentStart.x, point.y - segmentStart.y)
        }
        val projection = (
            (point.x - segmentStart.x) * segmentX +
                (point.y - segmentStart.y) * segmentY
            ) / segmentLengthSquared
        val ratio = projection.coerceIn(0.0, 1.0)
        val closestX = segmentStart.x + segmentX * ratio
        val closestY = segmentStart.y + segmentY * ratio
        return hypot(point.x - closestX, point.y - closestY)
    }

    private data class FlatPoint(val x: Double, val y: Double)
    private data class Direction(val north: Double, val east: Double)

    private const val METERS_PER_LATITUDE_DEGREE = 111_320.0
    private const val MIN_LONGITUDE_SCALE = 0.01
}
