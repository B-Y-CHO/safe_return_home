package com.bycho.safereturnhome.data

import kotlin.math.cos

/** WMS suggests waypoints only; the routing provider must validate the actual walkable road path. */
internal object SafeMapCandidatePlanner {
    fun corridorMeters(route: List<RouteCoordinate>): Double =
        (route.zipWithNext().sumOf { (from, to) -> routePointDistance(from, to) } * 0.18)
            .coerceIn(150.0, 450.0)

    fun plan(
        route: List<RouteCoordinate>,
        raster: SafeMapRiskRaster,
        maxWaypointCount: Int = 3
    ): List<List<RouteCoordinate>> {
        require(maxWaypointCount in 0..3)
        if (route.size < 2 || maxWaypointCount == 0 || SafeMapBounds.forRoutes(listOf(route)) == null) {
            return emptyList()
        }
        val exposure = raster.measure(route)
        // Do not generate apparent shortcuts through a poorly classified map.
        if (exposure.knownRatio < 0.65 || exposure.measuredMeters < 100.0) return emptyList()
        val start = route.first()
        val end = route.last()
        if (routePointDistance(start, end) < 100.0) return emptyList()
        val referenceGrade = exposure.weightedGradeMeters / exposure.knownMeters
        val corridor = corridorMeters(route)
        val nodes = raster.roadPoints().mapNotNull { point ->
            val location = locationAlongRoute(point, route)
            val progress = location.progress
            if (progress !in 0.08..0.92 || location.distanceMeters > corridor) return@mapNotNull null
            val grade = raster.gradeAt(point)
            // A candidate must improve on the observed route's weighted grade, not just be coloured.
            if ((grade / 10.0) * (grade / 10.0) >= referenceGrade) return@mapNotNull null
            val detour = routePointDistance(start, point) + routePointDistance(point, end)
            if (detour > exposure.measuredMeters + maxOf(100.0, exposure.measuredMeters * 0.30)) {
                return@mapNotNull null
            }
            Node(point, progress, grade * 100.0 + location.distanceMeters)
        }.groupBy { (it.progress * 8).toInt() }
            .toSortedMap()
            .values.flatMap { bucket -> bucket.sortedBy(Node::rank).take(8) }
            .sortedBy(Node::progress)
        if (nodes.isEmpty()) return emptyList()
        val allNodes = listOf(Node(start, 0.0, 0.0)) + nodes + Node(end, 1.0, 0.0)
        val costs = mutableMapOf<Pair<Int, Int>, Double>()
        fun edgeCost(from: Int, to: Int): Double = costs.getOrPut(from to to) {
            val measured = raster.measure(listOf(allNodes[from].point, allNodes[to].point), 40.0)
            measured.measuredMeters + measured.conservativeGradeMeters * 4.0
        }
        val plans = mutableListOf<List<RouteCoordinate>>()
        val excluded = mutableSetOf<Int>()
        repeat(2) {
            val path = bestPath(allNodes, maxWaypointCount, excluded, ::edgeCost)
            if (path.isEmpty()) return@repeat
            plans += path.map { allNodes[it].point }
            // An alternate must visit a different set of classified road locations.
            excluded += path
        }
        return plans
    }

    private fun bestPath(
        nodes: List<Node>,
        maxWaypointCount: Int,
        excluded: Set<Int>,
        edgeCost: (Int, Int) -> Double
    ): List<Int> {
        val destination = nodes.lastIndex
        val costs = Array(maxWaypointCount + 1) { DoubleArray(nodes.size) { Double.POSITIVE_INFINITY } }
        val parents = Array(maxWaypointCount + 1) { IntArray(nodes.size) { -1 } }
        costs[0][0] = 0.0
        for (count in 0 until maxWaypointCount) {
            for (from in 0 until destination) {
                if (!costs[count][from].isFinite()) continue
                for (to in from + 1 until destination) {
                    if (to in excluded || nodes[to].progress - nodes[from].progress < 0.08) continue
                    val cost = costs[count][from] + edgeCost(from, to) + 15.0
                    if (cost < costs[count + 1][to]) {
                        costs[count + 1][to] = cost
                        parents[count + 1][to] = from
                    }
                }
            }
        }
        var bestCount = 0
        var bestNode = -1
        var bestCost = Double.POSITIVE_INFINITY
        for (count in 1..maxWaypointCount) {
            for (from in 1 until destination) {
                if (!costs[count][from].isFinite()) continue
                val cost = costs[count][from] + edgeCost(from, destination)
                if (cost < bestCost) {
                    bestCost = cost
                    bestNode = from
                    bestCount = count
                }
            }
        }
        val path = mutableListOf<Int>()
        while (bestNode > 0 && bestCount > 0) {
            path += bestNode
            bestNode = parents[bestCount--][bestNode]
        }
        return path.asReversed()
    }

    private fun locationAlongRoute(point: RouteCoordinate, route: List<RouteCoordinate>): Location {
        val scale = 111_320.0 * cos(Math.toRadians(point.latitude))
        val total = route.zipWithNext().sumOf { (from, to) -> routePointDistance(from, to) }
        var travelled = 0.0
        var best = Location(0.0, Double.POSITIVE_INFINITY)
        for ((from, to) in route.zipWithNext()) {
            val x = (to.longitude - from.longitude) * scale
            val y = (to.latitude - from.latitude) * 111_320.0
            val px = (point.longitude - from.longitude) * scale
            val py = (point.latitude - from.latitude) * 111_320.0
            val lengthSquared = x * x + y * y
            val fraction = if (lengthSquared > 0.0) ((px * x + py * y) / lengthSquared).coerceIn(0.0, 1.0) else 0.0
            val length = routePointDistance(from, to)
            val distance = kotlin.math.hypot(px - x * fraction, py - y * fraction)
            if (distance < best.distanceMeters) best = Location((travelled + length * fraction) / total, distance)
            travelled += length
        }
        return best
    }

    private data class Node(val point: RouteCoordinate, val progress: Double, val rank: Double)
    private data class Location(val progress: Double, val distanceMeters: Double)
}
