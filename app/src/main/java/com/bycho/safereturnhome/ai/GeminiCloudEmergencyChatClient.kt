package com.bycho.safereturnhome.ai

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class EmergencyChatResponse(
    val assistantMessage: String,
    val isFinal: Boolean,
    val result: EmergencySituationResult?
)

class GeminiCloudEmergencyChatClient(
    private val baseAddress: String,
    private val client: OkHttpClient = defaultClient()
) {
    suspend fun chat(
        messages: List<EmergencyChatTurn>,
        input: EmergencySituationInput
    ): EmergencyChatResponse {
        val payload = JSONObject()
            .put(
                "messages",
                JSONArray().apply {
                    messages.takeLast(12).forEach { message ->
                        put(
                            JSONObject()
                                .put("role", message.role)
                                .put("content", message.content)
                        )
                    }
                }
            )
            .put(
                "context",
                JSONObject()
                    .put("latitude", input.latitude)
                    .put("longitude", input.longitude)
                    .put("accuracyMeters", input.accuracyMeters?.toDouble())
                    .put("isNavigationActive", input.isNavigationActive)
                    .put("destinationName", input.destinationName)
                    .put("remainingRouteDistanceMeters", input.remainingRouteDistanceMeters)
                    .put("routeDeviationCount", input.routeDeviationCount)
                    .put("hasVerifiedGuardian", input.hasVerifiedGuardian)
            )
            .toString()

        val body = payload.toRequestBody(JSON_MEDIA_TYPE)
        val responseBody = execute(
            Request.Builder()
                .url(endpoint("emergency/chat"))
                .post(body)
                .build()
        )
        return parseEmergencyChatResponse(responseBody)
    }

    private fun endpoint(path: String): String {
        val address = baseAddress.trim().trimEnd('/')
        require(address.isNotBlank()) { "Server address is blank." }
        val httpAddress = when {
            address.startsWith("ws://") -> address.replaceFirst("ws://", "http://")
            address.startsWith("wss://") -> address.replaceFirst("wss://", "https://")
            address.startsWith("http://") || address.startsWith("https://") -> address
            else -> "http://$address"
        }.removeSuffix("/ws")
        return "$httpAddress/$path"
    }

    private suspend fun execute(request: Request): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string().orEmpty()
                    if (!it.isSuccessful) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(IOException("HTTP ${it.code}: $body"))
                        }
                    } else if (continuation.isActive) {
                        continuation.resume(body)
                    }
                }
            }
        })
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .callTimeout(50, TimeUnit.SECONDS)
                .build()
        }
    }
}

fun parseEmergencyChatResponse(responseBody: String): EmergencyChatResponse {
    val json = JSONObject(responseBody)
    val resultJson = json.optJSONObject("result")
    return EmergencyChatResponse(
        assistantMessage = json.optString("assistantMessage").trim()
            .ifBlank { "상황을 조금 더 알려주세요." },
        isFinal = json.optBoolean("isFinal"),
        result = resultJson?.let(::parseEmergencySituationResult)
    )
}

private fun parseEmergencySituationResult(json: JSONObject): EmergencySituationResult {
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
        source = json.optString("source").trim().ifBlank { "GEMINI_API" }
    )
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T {
    val normalized = value.orEmpty().trim()
    return enumValues<T>().firstOrNull { it.name.equals(normalized, ignoreCase = true) } ?: default
}
