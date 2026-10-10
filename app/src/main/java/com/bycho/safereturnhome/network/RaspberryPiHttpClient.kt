package com.bycho.safereturnhome.network

import com.bycho.safereturnhome.data.DangerZone
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.coroutines.resume

data class HttpConnectionResult(
    val isSuccessful: Boolean,
    val message: String
)

data class SignalStatusResult(
    val status: String?,
    val message: String? = null,
    val errorMessage: String? = null
)

@Deprecated("Legacy creative-design client. Use EventHttpClient/FastApiDangerEventSource.")
class RaspberryPiHttpClient {
    fun checkConnection(serverAddress: String): HttpConnectionResult {
        var connection: HttpURLConnection? = null
        return try {
            val activeConnection = URL(statusEndpoint(serverAddress))
                .openConnection() as HttpURLConnection
            connection = activeConnection
            activeConnection.requestMethod = "GET"
            activeConnection.connectTimeout = CONNECTION_TIMEOUT_MILLIS
            activeConnection.readTimeout = CONNECTION_TIMEOUT_MILLIS
            activeConnection.connect()

            val statusCode = activeConnection.responseCode
            HttpConnectionResult(
                isSuccessful = statusCode in 200..299,
                message = "HTTP 응답 코드: $statusCode"
            )
        } catch (exception: Exception) {
            HttpConnectionResult(
                isSuccessful = false,
                message = exception.message ?: "서버에 연결할 수 없습니다."
            )
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun testWebSocketConnection(serverAddress: String): HttpConnectionResult = suspendCancellableCoroutine { continuation ->
        val address = serverAddress.trimEnd('/')
        val wsAddress = if (address.startsWith("ws://") || address.startsWith("wss://")) {
            address
        } else if (address.startsWith("https://")) {
            address.replaceFirst("https://", "wss://")
        } else if (address.startsWith("http://")) {
            address.replaceFirst("http://", "ws://")
        } else {
            "ws://$address"
        }
        val finalUrl = if (wsAddress.endsWith("/ws")) wsAddress else "$wsAddress/ws"

        val httpUrl = finalUrl.replaceFirst("ws://", "http://").replaceFirst("wss://", "https://")
        val client = OkHttpClient.Builder().connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS).build()
        val request = Request.Builder().url(httpUrl).build()

        var isResumed = false

        val call = client.newCall(request)
        call.enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: Response) {
                response.close()
                if (!isResumed) {
                    isResumed = true
                    continuation.resume(HttpConnectionResult(true, "서버 도달 성공! (코드: ${response.code})"))
                }
            }

            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                if (!isResumed) {
                    isResumed = true
                    continuation.resume(HttpConnectionResult(false, e.message ?: "네트워크 도달 실패"))
                }
            }
        })

        continuation.invokeOnCancellation { call.cancel() }
    }

    fun getServerStatus(serverAddress: String): SignalStatusResult {
        var connection: HttpURLConnection? = null
        return try {
            val activeConnection = URL(statusEndpoint(serverAddress))
                .openConnection() as HttpURLConnection
            connection = activeConnection
            activeConnection.requestMethod = "GET"
            activeConnection.connectTimeout = CONNECTION_TIMEOUT_MILLIS
            activeConnection.readTimeout = CONNECTION_TIMEOUT_MILLIS
            activeConnection.connect()

            val statusCode = activeConnection.responseCode
            if (statusCode !in 200..299) {
                return SignalStatusResult(
                    status = null,
                    errorMessage = "HTTP 응답 코드: $statusCode"
                )
            }

            val responseBody = activeConnection.inputStream
                .bufferedReader()
                .use { it.readText() }
            parseServerStatusResponse(responseBody)
        } catch (exception: Exception) {
            SignalStatusResult(
                status = null,
                errorMessage = exception.message ?: "서버에 연결할 수 없습니다."
            )
        } finally {
            connection?.disconnect()
        }
    }

    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 3_000
    }
}

fun statusEndpoint(serverAddress: String): String {
    return "${serverAddress.trimEnd('/')}/status"
}

fun parseServerStatusResponse(responseBody: String): SignalStatusResult {
    val response = JSONObject(responseBody)
    val status = response.optString("status").trim()
    if (status.isBlank()) {
        return SignalStatusResult(
            status = null,
            errorMessage = "응답에 status 값이 없습니다."
        )
    }

    return SignalStatusResult(
        status = status,
        message = response.optString("message").trim().ifBlank { null }
    )
}

fun parseDangerZoneMessage(responseBody: String): DangerZone? {
    val response = JSONObject(responseBody)
    val type = response.optString("type").trim()
    val status = response.optString("status").trim()

    if (type != DANGER_ZONE_MESSAGE_TYPE && !status.equals("DANGER", ignoreCase = true)) {
        return null
    }

    val hasLat = (response.has("lat") && !response.isNull("lat")) ||
                 (response.has("latitude") && !response.isNull("latitude"))
    val hasLon = (response.has("lon") && !response.isNull("lon")) ||
                 (response.has("longitude") && !response.isNull("longitude"))
    val hasRadius = (response.has("radius_m") && !response.isNull("radius_m")) ||
                    (response.has("radius") && !response.isNull("radius"))

    if (!hasLat || !hasLon || !hasRadius) return null

    val latitude = if (response.has("lat") && !response.isNull("lat")) {
        response.getDouble("lat")
    } else {
        response.getDouble("latitude")
    }
    val longitude = if (response.has("lon") && !response.isNull("lon")) {
        response.getDouble("lon")
    } else {
        response.getDouble("longitude")
    }
    val radiusMeters = if (response.has("radius_m") && !response.isNull("radius_m")) {
        response.getDouble("radius_m")
    } else {
        response.getDouble("radius")
    }

    require(latitude in -90.0..90.0) { "danger_zone lat is out of range" }
    require(longitude in -180.0..180.0) { "danger_zone lon is out of range" }
    require(radiusMeters > 0.0) { "danger_zone radius_m must be positive" }

    var id = response.optString("id").trim()
    if (id.isBlank()) {
        id = "cctv_danger_${System.currentTimeMillis()}"
    }

    return DangerZone(
        id = id,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        message = response.optString("message").trim().ifBlank { "🚨 미행 의심 위험지점이 감지되었습니다" }
    )
}

private const val DANGER_ZONE_MESSAGE_TYPE = "danger_zone"
