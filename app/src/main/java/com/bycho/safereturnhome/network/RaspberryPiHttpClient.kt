package com.bycho.safereturnhome.network

import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class HttpConnectionResult(
    val isSuccessful: Boolean,
    val message: String
)

data class SignalStatusResult(
    val status: String?,
    val message: String? = null,
    val errorMessage: String? = null
)

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
