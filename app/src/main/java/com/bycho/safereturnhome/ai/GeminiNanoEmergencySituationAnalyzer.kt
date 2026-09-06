package com.bycho.safereturnhome.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.first

class GeminiNanoEmergencySituationAnalyzer : EmergencySituationAnalyzer {
    private val generativeModel = Generation.getClient()

    override suspend fun analyze(input: EmergencySituationInput): EmergencySituationResult {
        ensureAvailable()
        val response = generativeModel.generateContent(
            generateContentRequest(TextPart(buildPrompt(input))) {
                temperature = 0.0f
                topK = 1
                candidateCount = 1
                maxOutputTokens = 180
            }
        )
        val text = response.candidates.firstOrNull()?.text.orEmpty()
        return parseEmergencySituationResult(text, fallbackSource = "GEMINI_NANO")
    }

    private suspend fun ensureAvailable() {
        when (val status = generativeModel.checkStatus()) {
            FeatureStatus.AVAILABLE -> return
            FeatureStatus.DOWNLOADABLE -> {
                val downloadStatus = generativeModel.download().first {
                    it is DownloadStatus.DownloadCompleted || it is DownloadStatus.DownloadFailed
                }
                if (downloadStatus is DownloadStatus.DownloadFailed) {
                    throw downloadStatus.e
                }
            }
            FeatureStatus.DOWNLOADING -> {
                throw IllegalStateException("Gemini Nano is still downloading.")
            }
            FeatureStatus.UNAVAILABLE -> {
                throw IllegalStateException("Gemini Nano is not available on this device.")
            }
            else -> {
                throw IllegalStateException("Gemini Nano status is unsupported: $status")
            }
        }
    }

    private fun buildPrompt(input: EmergencySituationInput): String {
        return """
            You are an on-device safety classifier for a Korean safe-return-home app.
            Do not make a final emergency decision. Classify the situation conservatively.
            Return only one JSON object. No markdown.

            JSON schema:
            {
              "type": "SOS_REQUEST|ROUTE_DEVIATION|STATIONARY_RISK|USER_DISTRESS|UNKNOWN",
              "severity": "LOW|MEDIUM|HIGH|CRITICAL",
              "confidence": 0.0,
              "summary": "Korean one-sentence summary",
              "recommendedAction": "Korean short action for the user",
              "shouldNotifyGuardian": true,
              "shouldCreateDangerZone": true,
              "source": "GEMINI_NANO"
            }

            Rules:
            - Direct SOS or explicit fear/help request should be HIGH or CRITICAL.
            - Do not claim police, medical, or legal certainty.
            - Prefer guardian notification for HIGH or CRITICAL.
            - Prefer emergency call recommendation for CRITICAL.

            Input:
            trigger=${input.trigger}
            userMessage=${input.userMessage.orEmpty()}
            latitude=${input.latitude ?: "unknown"}
            longitude=${input.longitude ?: "unknown"}
            accuracyMeters=${input.accuracyMeters ?: "unknown"}
            isNavigationActive=${input.isNavigationActive}
            destinationName=${input.destinationName.orEmpty()}
            remainingRouteDistanceMeters=${input.remainingRouteDistanceMeters ?: "unknown"}
            routeDeviationCount=${input.routeDeviationCount}
            hasVerifiedGuardian=${input.hasVerifiedGuardian}
        """.trimIndent()
    }

    companion object {
        private const val LOG_TAG = "GeminiNanoEmergency"
    }
}

class HybridEmergencySituationAnalyzer(
    private val primary: EmergencySituationAnalyzer = GeminiNanoEmergencySituationAnalyzer(),
    private val fallback: EmergencySituationAnalyzer = RuleBasedEmergencySituationAnalyzer()
) : EmergencySituationAnalyzer {
    override suspend fun analyze(input: EmergencySituationInput): EmergencySituationResult {
        return runCatching { primary.analyze(input) }
            .onFailure { error ->
                Log.w("HybridEmergencyAnalyzer", "Gemini Nano analysis failed. Falling back.", error)
            }
            .getOrElse { fallback.analyze(input).copy(source = "RULE_BASED_FALLBACK") }
    }
}
