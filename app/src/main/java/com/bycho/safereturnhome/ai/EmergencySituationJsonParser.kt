package com.bycho.safereturnhome.ai

import org.json.JSONObject

fun parseEmergencySituationResult(
    modelOutput: String,
    fallbackSource: String = "GEMINI_NANO"
): EmergencySituationResult {
    val jsonText = modelOutput.extractJsonObjectText()
    val json = JSONObject(jsonText)
    val severity = enumValueOrDefault(
        value = json.optString("severity"),
        default = EmergencySeverity.MEDIUM
    )
    val type = enumValueOrDefault(
        value = json.optString("type"),
        default = EmergencySituationType.UNKNOWN
    )
    return EmergencySituationResult(
        type = type,
        severity = severity,
        confidence = json.optDouble("confidence", 0.5).coerceIn(0.0, 1.0),
        summary = json.optString("summary").trim()
            .ifBlank { "상황 확인이 필요한 상태입니다." },
        recommendedAction = json.optString("recommendedAction").trim()
            .ifBlank { "현재 위치와 주변 안전지점을 확인하세요." },
        shouldNotifyGuardian = json.optBoolean(
            "shouldNotifyGuardian",
            severity >= EmergencySeverity.HIGH
        ),
        shouldCreateDangerZone = json.optBoolean(
            "shouldCreateDangerZone",
            severity >= EmergencySeverity.HIGH
        ),
        source = json.optString("source").trim().ifBlank { fallbackSource }
    )
}

private fun String.extractJsonObjectText(): String {
    val start = indexOf('{')
    val end = lastIndexOf('}')
    require(start >= 0 && end > start) { "Model output does not contain a JSON object." }
    return substring(start, end + 1)
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
    val normalized = value.orEmpty().trim()
    return enumValues<T>().firstOrNull { it.name.equals(normalized, ignoreCase = true) } ?: default
}
