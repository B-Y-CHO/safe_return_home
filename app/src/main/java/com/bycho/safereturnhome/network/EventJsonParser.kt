package com.bycho.safereturnhome.network

import com.bycho.safereturnhome.data.DangerSeverity
import com.bycho.safereturnhome.data.DangerZone
import com.bycho.safereturnhome.data.DangerZoneType
import org.json.JSONArray
import org.json.JSONObject

fun parseDangerZone(json: JSONObject): DangerZone {
    val latitude = json.doubleValue("latitude", "lat")
    val longitude = json.doubleValue("longitude", "lon")
    val radiusMeters = json.doubleValue("radiusMeters", "radius_m", "radius")
    require(latitude in -90.0..90.0) { "latitude is out of range" }
    require(longitude in -180.0..180.0) { "longitude is out of range" }
    require(radiusMeters > 0.0) { "radiusMeters must be positive" }

    return DangerZone(
        id = json.optString("id").trim().ifBlank { "danger_${System.currentTimeMillis()}" },
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        message = json.optString("message").trim().ifBlank { "위험구역이 감지되었습니다." },
        type = DangerZoneType.from(json.optString("type")),
        source = json.optString("source").trim().ifBlank { "UNKNOWN" },
        severity = DangerSeverity.from(json.optString("severity")),
        timestamp = json.optString("timestamp").trim().ifBlank { null }
    )
}

fun parseDangerZoneList(responseBody: String): List<DangerZone> {
    val array = JSONArray(responseBody)
    return (0 until array.length()).map { parseDangerZone(array.getJSONObject(it)) }
}

fun parseDangerEventMessage(message: String): DangerZone? {
    val root = JSONObject(message)
    return when (root.optString("type")) {
        "danger_zone_created" -> root.optJSONObject("payload")?.let(::parseDangerZone)
        "LAMP_FAULT", "DANGER_EVENT", "CCTV_BLIND_SPOT", "PATROL_WARNING" ->
            parseDangerZone(root)
        else -> null
    }
}

private fun JSONObject.doubleValue(vararg names: String): Double {
    val name = names.firstOrNull { has(it) && !isNull(it) }
        ?: throw IllegalArgumentException("Missing ${names.joinToString("/")}")
    return getDouble(name)
}
