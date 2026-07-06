package com.bycho.safereturnhome.data

data class DangerZone(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val message: String
)

data class RouteCoordinate(
    val latitude: Double,
    val longitude: Double
)
