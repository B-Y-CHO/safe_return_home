package com.bycho.safereturnhome.data

import java.util.PriorityQueue
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min

data class CctvSafeRoutePlan(
    val waypoints: List<CctvCoordinate>,
    val estimatedDistanceMeters: Int,
    val estimatedCost: Double,
    val estimatedCoverageRatio: Double,
    val estimatedStreetlightCoverageRatio: Double,
    val candidateCctvCount: Int
)

object CctvSafeRoutePlanner {
    fun countRelevantCoordinates(
        startLatitude: Double,
        startLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double,
        cctvCoordinates: List<CctvCoordinate>
    ): Int {
        val routeContext = createRouteContext(
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            destinationLatitude = destinationLatitude,
            destinationLongitude = destinationLongitude,
            cctvCoordinates = cctvCoordinates
        )
        return createCandidateNodes(
            destination = routeContext.destination,
            projectedCctvCoordinates = routeContext.projectedCctvCoordinates,
            projectedStreetlightCoordinates = emptyList()
        ).size
    }

    fun plan(
        startLatitude: Double,
        startLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double,
        cctvCoordinates: List<CctvCoordinate>,
        streetlightCoordinates: List<StreetlightCoordinate> = emptyList(),
        maxWaypointCount: Int
    ): CctvSafeRoutePlan {
        require(maxWaypointCount >= 0)
        val routeContext = createRouteContext(
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            destinationLatitude = destinationLatitude,
            destinationLongitude = destinationLongitude,
            cctvCoordinates = cctvCoordinates
        )
        val start = routeContext.start
        val destination = routeContext.destination
        if (destination.point.distanceTo(start.point) < 1.0) {
            return CctvSafeRoutePlan(
                waypoints = emptyList(),
                estimatedDistanceMeters = 0,
                estimatedCost = 0.0,
                estimatedCoverageRatio = 1.0,
                estimatedStreetlightCoverageRatio = 1.0,
                candidateCctvCount = 0
            )
        }

        val projectedCctvCoordinates = routeContext.projectedCctvCoordinates
        val projectedStreetlightCoordinates = projectStreetlightCoordinates(
            projection = routeContext.projection,
            streetlightCoordinates = streetlightCoordinates
        )
        val cctvNodes = createCandidateNodes(
            destination = destination,
            projectedCctvCoordinates = projectedCctvCoordinates,
            projectedStreetlightCoordinates = projectedStreetlightCoordinates
        )
        val nodes = listOf(start) + cctvNodes + destination
        val destinationIndex = nodes.lastIndex
        val routeState = findBestRoute(
            nodes = nodes,
            destinationIndex = destinationIndex,
            projectedCctvCoordinates = projectedCctvCoordinates,
            projectedStreetlightCoordinates = projectedStreetlightCoordinates,
            maxWaypointCount = maxWaypointCount
        )
        val routeNodes = restoreRouteNodes(routeState, nodes)
        val segmentMetrics = routeNodes.zipWithNext { from, to ->
            calculateSegmentMetrics(
                from = from.point,
                to = to.point,
                projectedCctvCoordinates = projectedCctvCoordinates,
                projectedStreetlightCoordinates = projectedStreetlightCoordinates
            )
        }
        val estimatedDistanceMeters = segmentMetrics.sumOf(SegmentMetrics::distanceMeters)
        val weightedCoverage = segmentMetrics.sumOf { segment ->
            segment.coverageRatio * segment.distanceMeters
        }
        val weightedStreetlightCoverage = segmentMetrics.sumOf { segment ->
            segment.streetlightCoverageRatio * segment.distanceMeters
        }
        return CctvSafeRoutePlan(
            waypoints = routeNodes.mapNotNull(RouteNode::coordinate),
            estimatedDistanceMeters = estimatedDistanceMeters.toInt(),
            estimatedCost = routeState.gScore,
            estimatedCoverageRatio = if (estimatedDistanceMeters > 0.0) {
                weightedCoverage / estimatedDistanceMeters
            } else {
                1.0
            },
            estimatedStreetlightCoverageRatio = if (estimatedDistanceMeters > 0.0) {
                weightedStreetlightCoverage / estimatedDistanceMeters
            } else {
                1.0
            },
            candidateCctvCount = cctvNodes.size
        )
    }

    private fun createRouteContext(
        startLatitude: Double,
        startLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double,
        cctvCoordinates: List<CctvCoordinate>
    ): RouteContext {
        val projection = Projection(
            originLatitude = startLatitude,
            originLongitude = startLongitude,
            averageLatitude = (startLatitude + destinationLatitude) / 2.0
        )
        return RouteContext(
            projection = projection,
            start = RouteNode.start(),
            destination = RouteNode.destination(
                point = projection.project(destinationLatitude, destinationLongitude)
            ),
            projectedCctvCoordinates = cctvCoordinates
                .distinctBy(CctvCoordinate::address)
                .map { coordinate ->
                    ProjectedCctvCoordinate(
                        coordinate = coordinate,
                        point = projection.project(coordinate.latitude, coordinate.longitude)
                    )
                }
        )
    }

    private fun projectStreetlightCoordinates(
        projection: Projection,
        streetlightCoordinates: List<StreetlightCoordinate>
    ): List<ProjectedStreetlightCoordinate> {
        return streetlightCoordinates
            .distinctBy { coordinate ->
                Triple(coordinate.latitude, coordinate.longitude, coordinate.address)
            }
            .map { coordinate ->
                ProjectedStreetlightCoordinate(
                    coordinate = coordinate,
                    point = projection.project(coordinate.latitude, coordinate.longitude)
                )
            }
    }

    private fun createCandidateNodes(
        destination: RouteNode,
        projectedCctvCoordinates: List<ProjectedCctvCoordinate>,
        projectedStreetlightCoordinates: List<ProjectedStreetlightCoordinate>
    ): List<RouteNode> {
        val directDistance = destination.point.distanceTo(RoutePoint.ORIGIN)
        val maxDistanceFromDirectPath = (directDistance * 0.45).coerceIn(300.0, 900.0)
        val maxDetourDistance = (directDistance * 0.7).coerceAtLeast(600.0)
        return projectedCctvCoordinates
            .mapNotNull { projectedCoordinate ->
                val progress = projectionProgress(
                    point = projectedCoordinate.point,
                    destination = destination.point
                )
                if (progress !in MIN_ROUTE_PROGRESS..MAX_ROUTE_PROGRESS) return@mapNotNull null
                val distanceFromDirectPath = distanceFromLine(
                    point = projectedCoordinate.point,
                    lineEnd = destination.point
                )
                if (distanceFromDirectPath > maxDistanceFromDirectPath) return@mapNotNull null
                val detourDistance = projectedCoordinate.point.distanceTo(RoutePoint.ORIGIN) +
                    projectedCoordinate.point.distanceTo(destination.point) -
                    directDistance
                if (detourDistance > maxDetourDistance) return@mapNotNull null
                val nearbyCameraCount = projectedCctvCoordinates.sumOf { nearbyCoordinate ->
                    if (
                        nearbyCoordinate.point.distanceTo(projectedCoordinate.point) <=
                        CCTV_COVERAGE_RADIUS_METERS
                    ) {
                        nearbyCoordinate.coordinate.cameraCount
                    } else {
                        0
                    }
                }
                val nearbyLightCount = projectedStreetlightCoordinates.sumOf { nearbyCoordinate ->
                    if (
                        nearbyCoordinate.point.distanceTo(projectedCoordinate.point) <=
                        STREETLIGHT_COVERAGE_RADIUS_METERS
                    ) {
                        nearbyCoordinate.coordinate.lightCount
                    } else {
                        0
                    }
                }
                RouteNode.cctv(
                    coordinate = projectedCoordinate.coordinate,
                    point = projectedCoordinate.point,
                    progress = progress,
                    candidateRank = distanceFromDirectPath -
                        nearbyCameraCount * 45.0 -
                        nearbyLightCount.coerceAtMost(MAX_LIGHT_COUNT_FOR_CANDIDATE_RANK) * 8.0
                )
            }
            .sortedBy(RouteNode::candidateRank)
            .take(MAX_GRAPH_CCTV_NODE_COUNT)
            .sortedBy(RouteNode::progress)
    }

    private fun findBestRoute(
        nodes: List<RouteNode>,
        destinationIndex: Int,
        projectedCctvCoordinates: List<ProjectedCctvCoordinate>,
        projectedStreetlightCoordinates: List<ProjectedStreetlightCoordinate>,
        maxWaypointCount: Int
    ): SearchResult {
        val startState = RouteState(nodeIndex = 0, waypointCount = 0)
        val destinationPoint = nodes[destinationIndex].point
        val openSet = PriorityQueue(compareBy<QueueEntry>(QueueEntry::fScore))
        val gScores = mutableMapOf(startState to 0.0)
        val parents = mutableMapOf<RouteState, RouteState>()
        openSet += QueueEntry(
            state = startState,
            gScore = 0.0,
            fScore = nodes.first().point.distanceTo(destinationPoint)
        )

        while (openSet.isNotEmpty()) {
            val current = openSet.remove()
            if (current.gScore > gScores.getValue(current.state)) continue
            if (current.state.nodeIndex == destinationIndex) {
                return SearchResult(
                    destinationState = current.state,
                    gScore = current.gScore,
                    parents = parents
                )
            }
            val currentNode = nodes[current.state.nodeIndex]
            neighborIndexes(
                currentState = current.state,
                nodes = nodes,
                destinationIndex = destinationIndex,
                maxWaypointCount = maxWaypointCount
            ).forEach { neighborIndex ->
                val neighbor = nodes[neighborIndex]
                val segment = calculateSegmentMetrics(
                    from = currentNode.point,
                    to = neighbor.point,
                    projectedCctvCoordinates = projectedCctvCoordinates,
                    projectedStreetlightCoordinates = projectedStreetlightCoordinates
                )
                val waypointCount = current.state.waypointCount +
                    if (neighbor.coordinate == null) 0 else 1
                val neighborState = RouteState(
                    nodeIndex = neighborIndex,
                    waypointCount = waypointCount
                )
                val tentativeGScore = current.gScore +
                    segment.cost +
                    if (neighbor.coordinate == null) 0.0 else WAYPOINT_COST_METERS
                if (tentativeGScore >= gScores.getOrDefault(neighborState, Double.MAX_VALUE)) {
                    return@forEach
                }
                parents[neighborState] = current.state
                gScores[neighborState] = tentativeGScore
                openSet += QueueEntry(
                    state = neighborState,
                    gScore = tentativeGScore,
                    fScore = tentativeGScore + neighbor.point.distanceTo(destinationPoint)
                )
            }
        }
        error("A* 경로를 계산하지 못했습니다")
    }

    private fun neighborIndexes(
        currentState: RouteState,
        nodes: List<RouteNode>,
        destinationIndex: Int,
        maxWaypointCount: Int
    ): List<Int> {
        val currentNode = nodes[currentState.nodeIndex]
        if (currentState.waypointCount >= maxWaypointCount) return listOf(destinationIndex)
        val destinationDistance = nodes[destinationIndex].point.distanceTo(RoutePoint.ORIGIN)
        val maxEdgeDistance = (destinationDistance * 0.75).coerceAtLeast(500.0)
        return buildList {
            add(destinationIndex)
            nodes.forEachIndexed { index, node ->
                if (
                    node.coordinate != null &&
                    node.progress >= currentNode.progress + MIN_WAYPOINT_PROGRESS_GAP &&
                    node.point.distanceTo(currentNode.point) <= maxEdgeDistance
                ) {
                    add(index)
                }
            }
        }
    }

    private fun calculateSegmentMetrics(
        from: RoutePoint,
        to: RoutePoint,
        projectedCctvCoordinates: List<ProjectedCctvCoordinate>,
        projectedStreetlightCoordinates: List<ProjectedStreetlightCoordinate>
    ): SegmentMetrics {
        val distanceMeters = from.distanceTo(to)
        if (distanceMeters < 1.0) {
            return SegmentMetrics(
                distanceMeters = 0.0,
                coverageRatio = 1.0,
                streetlightCoverageRatio = 1.0,
                cost = 0.0
            )
        }
        val sampleCount = ceil(distanceMeters / COVERAGE_SAMPLE_INTERVAL_METERS)
            .toInt()
            .coerceIn(MIN_COVERAGE_SAMPLE_COUNT, MAX_COVERAGE_SAMPLE_COUNT)
        var coverageScore = 0.0
        var streetlightCoverageScore = 0.0
        for (sampleIndex in 0..sampleCount) {
            val ratio = sampleIndex.toDouble() / sampleCount
            val samplePoint = RoutePoint(
                x = from.x + (to.x - from.x) * ratio,
                y = from.y + (to.y - from.y) * ratio
            )
            val nearbyCameraCount = projectedCctvCoordinates.sumOf { coordinate ->
                if (coordinate.point.distanceTo(samplePoint) <= CCTV_COVERAGE_RADIUS_METERS) {
                    coordinate.coordinate.cameraCount
                } else {
                    0
                }
            }
            coverageScore += min(nearbyCameraCount / CAMERA_COUNT_FOR_FULL_COVERAGE, 1.0)
            val nearbyLightCount = projectedStreetlightCoordinates.sumOf { coordinate ->
                if (coordinate.point.distanceTo(samplePoint) <= STREETLIGHT_COVERAGE_RADIUS_METERS) {
                    coordinate.coordinate.lightCount
                } else {
                    0
                }
            }
            streetlightCoverageScore += min(nearbyLightCount / LIGHT_COUNT_FOR_FULL_COVERAGE, 1.0)
        }
        val coverageRatio = coverageScore / (sampleCount + 1)
        val streetlightCoverageRatio = streetlightCoverageScore / (sampleCount + 1)
        val preferenceCoverageRatio = (coverageRatio +
            streetlightCoverageRatio * STREETLIGHT_PREFERENCE_WEIGHT
        ).coerceAtMost(1.0)
        return SegmentMetrics(
            distanceMeters = distanceMeters,
            coverageRatio = coverageRatio,
            streetlightCoverageRatio = streetlightCoverageRatio,
            cost = distanceMeters * (1.0 + (1.0 - preferenceCoverageRatio) * UNCOVERED_DISTANCE_PENALTY)
        )
    }

    private fun restoreRouteNodes(
        searchResult: SearchResult,
        nodes: List<RouteNode>
    ): List<RouteNode> {
        val routeStates = mutableListOf<RouteState>()
        var currentState: RouteState? = searchResult.destinationState
        while (currentState != null) {
            routeStates += currentState
            currentState = searchResult.parents[currentState]
        }
        return routeStates.asReversed().map { state -> nodes[state.nodeIndex] }
    }

    private fun projectionProgress(point: RoutePoint, destination: RoutePoint): Double {
        val destinationDistanceSquared = destination.x * destination.x + destination.y * destination.y
        return (point.x * destination.x + point.y * destination.y) / destinationDistanceSquared
    }

    private fun distanceFromLine(point: RoutePoint, lineEnd: RoutePoint): Double {
        val progress = projectionProgress(point, lineEnd)
        return point.distanceTo(
            RoutePoint(
                x = lineEnd.x * progress,
                y = lineEnd.y * progress
            )
        )
    }

    private data class Projection(
        val originLatitude: Double,
        val originLongitude: Double,
        val averageLatitude: Double
    ) {
        private val longitudeMetersPerDegree = METERS_PER_LATITUDE_DEGREE *
            cos(Math.toRadians(averageLatitude))

        fun project(latitude: Double, longitude: Double): RoutePoint {
            return RoutePoint(
                x = (longitude - originLongitude) * longitudeMetersPerDegree,
                y = (latitude - originLatitude) * METERS_PER_LATITUDE_DEGREE
            )
        }
    }

    private data class ProjectedCctvCoordinate(
        val coordinate: CctvCoordinate,
        val point: RoutePoint
    )

    private data class ProjectedStreetlightCoordinate(
        val coordinate: StreetlightCoordinate,
        val point: RoutePoint
    )

    private data class RouteContext(
        val projection: Projection,
        val start: RouteNode,
        val destination: RouteNode,
        val projectedCctvCoordinates: List<ProjectedCctvCoordinate>
    )

    private data class RoutePoint(
        val x: Double,
        val y: Double
    ) {
        fun distanceTo(other: RoutePoint): Double = hypot(x - other.x, y - other.y)

        companion object {
            val ORIGIN = RoutePoint(x = 0.0, y = 0.0)
        }
    }

    private data class RouteNode(
        val coordinate: CctvCoordinate?,
        val point: RoutePoint,
        val progress: Double,
        val candidateRank: Double
    ) {
        companion object {
            fun start() = RouteNode(
                coordinate = null,
                point = RoutePoint.ORIGIN,
                progress = 0.0,
                candidateRank = 0.0
            )

            fun destination(point: RoutePoint) = RouteNode(
                coordinate = null,
                point = point,
                progress = 1.0,
                candidateRank = 0.0
            )

            fun cctv(
                coordinate: CctvCoordinate,
                point: RoutePoint,
                progress: Double,
                candidateRank: Double
            ) = RouteNode(
                coordinate = coordinate,
                point = point,
                progress = progress,
                candidateRank = candidateRank
            )
        }
    }

    private data class RouteState(
        val nodeIndex: Int,
        val waypointCount: Int
    )

    private data class QueueEntry(
        val state: RouteState,
        val gScore: Double,
        val fScore: Double
    )

    private data class SearchResult(
        val destinationState: RouteState,
        val gScore: Double,
        val parents: Map<RouteState, RouteState>
    )

    private data class SegmentMetrics(
        val distanceMeters: Double,
        val coverageRatio: Double,
        val streetlightCoverageRatio: Double,
        val cost: Double
    )

    private const val METERS_PER_LATITUDE_DEGREE = 111_320.0
    private const val MIN_ROUTE_PROGRESS = 0.05
    private const val MAX_ROUTE_PROGRESS = 0.95
    private const val MIN_WAYPOINT_PROGRESS_GAP = 0.08
    private const val MAX_GRAPH_CCTV_NODE_COUNT = 72
    private const val CCTV_COVERAGE_RADIUS_METERS = 180.0
    private const val STREETLIGHT_COVERAGE_RADIUS_METERS = 180.0
    private const val COVERAGE_SAMPLE_INTERVAL_METERS = 70.0
    private const val MIN_COVERAGE_SAMPLE_COUNT = 2
    private const val MAX_COVERAGE_SAMPLE_COUNT = 20
    private const val CAMERA_COUNT_FOR_FULL_COVERAGE = 2.0
    private const val LIGHT_COUNT_FOR_FULL_COVERAGE = 4.0
    private const val STREETLIGHT_PREFERENCE_WEIGHT = 0.35
    private const val MAX_LIGHT_COUNT_FOR_CANDIDATE_RANK = 8
    private const val UNCOVERED_DISTANCE_PENALTY = 2.2
    private const val WAYPOINT_COST_METERS = 15.0
}
