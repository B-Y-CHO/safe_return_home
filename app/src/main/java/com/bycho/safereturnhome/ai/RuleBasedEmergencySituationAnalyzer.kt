package com.bycho.safereturnhome.ai

class RuleBasedEmergencySituationAnalyzer : EmergencySituationAnalyzer {
    override suspend fun analyze(input: EmergencySituationInput): EmergencySituationResult {
        val message = input.userMessage.orEmpty()
        val severity = when {
            input.trigger.equals("SOS_BUTTON", ignoreCase = true) -> EmergencySeverity.HIGH
            input.routeDeviationCount >= 3 -> EmergencySeverity.HIGH
            message.containsAny("살려", "도와", "위험", "따라", "쫓아", "폭행", "납치") ->
                EmergencySeverity.HIGH
            input.isNavigationActive && input.remainingRouteDistanceMeters == null ->
                EmergencySeverity.MEDIUM
            else -> EmergencySeverity.MEDIUM
        }
        val type = when {
            input.trigger.equals("SOS_BUTTON", ignoreCase = true) -> EmergencySituationType.SOS_REQUEST
            input.routeDeviationCount >= 3 -> EmergencySituationType.ROUTE_DEVIATION
            message.containsAny("도와", "살려", "위험") -> EmergencySituationType.USER_DISTRESS
            else -> EmergencySituationType.UNKNOWN
        }
        return EmergencySituationResult(
            type = type,
            severity = severity,
            confidence = if (input.trigger.equals("SOS_BUTTON", ignoreCase = true)) 0.88 else 0.62,
            summary = summaryFor(type, severity, input),
            recommendedAction = recommendedActionFor(severity, input.hasVerifiedGuardian),
            shouldNotifyGuardian = severity >= EmergencySeverity.HIGH || input.hasVerifiedGuardian,
            shouldCreateDangerZone = severity >= EmergencySeverity.HIGH,
            source = "RULE_BASED"
        )
    }

    private fun summaryFor(
        type: EmergencySituationType,
        severity: EmergencySeverity,
        input: EmergencySituationInput
    ): String {
        return when (type) {
            EmergencySituationType.SOS_REQUEST -> "사용자가 SOS를 직접 요청했습니다."
            EmergencySituationType.ROUTE_DEVIATION -> "경로 이탈이 반복되어 위험 가능성이 있습니다."
            EmergencySituationType.USER_DISTRESS -> "사용자 메시지에 긴급 도움 요청 표현이 포함되어 있습니다."
            EmergencySituationType.STATIONARY_RISK -> "이동 정체가 감지되어 확인이 필요합니다."
            EmergencySituationType.UNKNOWN ->
                if (severity >= EmergencySeverity.HIGH) {
                    "위험 가능성이 높은 상황으로 분류되었습니다."
                } else {
                    "상황 확인이 필요한 상태입니다."
                }
        }
    }

    private fun recommendedActionFor(
        severity: EmergencySeverity,
        hasVerifiedGuardian: Boolean
    ): String {
        return when {
            severity == EmergencySeverity.CRITICAL ->
                "즉시 112 또는 119에 연결하고 보호자에게 위치를 공유하세요."
            severity == EmergencySeverity.HIGH && hasVerifiedGuardian ->
                "보호자에게 위치를 전송하고 필요하면 112에 연결하세요."
            severity == EmergencySeverity.HIGH ->
                "긴급 연락처를 확인하고 필요하면 112에 연결하세요."
            else -> "현재 위치와 주변 안전지점을 확인하세요."
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { contains(it, ignoreCase = true) }
    }
}
