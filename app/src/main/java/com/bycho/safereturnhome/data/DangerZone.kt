package com.bycho.safereturnhome.data

data class DangerZone(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val message: String,
    val type: DangerZoneType = DangerZoneType.DANGER_EVENT,
    val source: String = "UNKNOWN",
    val severity: DangerSeverity = DangerSeverity.MEDIUM,
    val timestamp: String? = null
)

enum class DangerZoneType {
    LAMP_FAULT,
    DANGER_EVENT,
    CCTV_BLIND_SPOT,
    PATROL_WARNING;

    companion object {
        fun from(value: String?): DangerZoneType = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: DANGER_EVENT
    }
}

enum class DangerSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    companion object {
        fun from(value: String?): DangerSeverity = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: MEDIUM
    }
}

data class RouteCoordinate(
    val latitude: Double,
    val longitude: Double
)
