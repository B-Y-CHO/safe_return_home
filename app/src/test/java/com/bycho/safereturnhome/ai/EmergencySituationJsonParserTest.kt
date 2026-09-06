package com.bycho.safereturnhome.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencySituationJsonParserTest {
    @Test
    fun parsesJsonObjectFromModelOutput() {
        val result = parseEmergencySituationResult(
            """
            ```json
            {
              "type": "USER_DISTRESS",
              "severity": "HIGH",
              "confidence": 0.91,
              "summary": "사용자가 누군가 따라온다고 보고했습니다.",
              "recommendedAction": "보호자에게 위치를 공유하고 밝은 곳으로 이동하세요.",
              "shouldNotifyGuardian": true,
              "shouldCreateDangerZone": true
            }
            ```
            """.trimIndent()
        )

        assertEquals(EmergencySituationType.USER_DISTRESS, result.type)
        assertEquals(EmergencySeverity.HIGH, result.severity)
        assertEquals("GEMINI_NANO", result.source)
        assertTrue(result.shouldNotifyGuardian)
        assertTrue(result.shouldCreateDangerZone)
    }

    @Test
    fun clampsConfidence() {
        val result = parseEmergencySituationResult(
            """
            {
              "type": "SOS_REQUEST",
              "severity": "CRITICAL",
              "confidence": 3.4,
              "summary": "긴급 도움 요청입니다.",
              "recommendedAction": "112에 연결하세요."
            }
            """.trimIndent()
        )

        assertEquals(1.0, result.confidence, 0.0)
    }

    @Test
    fun parsesEmergencyChatResponse() {
        val response = parseEmergencyChatResponse(
            """
            {
              "assistantMessage": "보호자에게 알리고 밝은 곳으로 이동하세요.",
              "isFinal": true,
              "result": {
                "type": "SOS_REQUEST",
                "severity": "CRITICAL",
                "confidence": 0.94,
                "summary": "사용자가 즉시 도움이 필요한 상황입니다.",
                "recommendedAction": "112에 전화하고 보호자에게 위치를 공유하세요.",
                "shouldNotifyGuardian": true,
                "shouldCreateDangerZone": true,
                "source": "GEMINI_API"
              }
            }
            """.trimIndent()
        )

        assertTrue(response.isFinal)
        assertEquals("보호자에게 알리고 밝은 곳으로 이동하세요.", response.assistantMessage)
        assertEquals(EmergencySituationType.SOS_REQUEST, response.result?.type)
        assertEquals(EmergencySeverity.CRITICAL, response.result?.severity)
        assertEquals("GEMINI_API", response.result?.source)
    }
}
