package com.bycho.safereturnhome.ai

enum class EmergencySituationType {
    SOS_REQUEST,
    ROUTE_DEVIATION,
    STATIONARY_RISK,
    USER_DISTRESS,
    UNKNOWN
}

enum class EmergencySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

data class EmergencySituationInput(
    val trigger: String,
    val userMessage: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMeters: Float? = null,
    val isNavigationActive: Boolean = false,
    val destinationName: String? = null,
    val remainingRouteDistanceMeters: Int? = null,
    val routeDeviationCount: Int = 0,
    val hasVerifiedGuardian: Boolean = false
)

data class EmergencyChatTurn(
    val role: String,
    val content: String
)

data class EmergencySituationResult(
    val type: EmergencySituationType,
    val severity: EmergencySeverity,
    val confidence: Double,
    val summary: String,
    val recommendedAction: String,
    val shouldNotifyGuardian: Boolean,
    val shouldCreateDangerZone: Boolean,
    val source: String
) {
    fun userNotice(): String {
        return "AI 상황 분석: ${severity.label} - $summary\n권장 조치: $recommendedAction"
    }
}

interface EmergencySituationAnalyzer {
    suspend fun analyze(input: EmergencySituationInput): EmergencySituationResult
}

internal val EmergencySeverity.label: String
    get() = when (this) {
        EmergencySeverity.LOW -> "낮음"
        EmergencySeverity.MEDIUM -> "주의"
        EmergencySeverity.HIGH -> "높음"
        EmergencySeverity.CRITICAL -> "긴급"
    }
