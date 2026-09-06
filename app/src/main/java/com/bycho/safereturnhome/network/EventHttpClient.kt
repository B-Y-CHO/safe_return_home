package com.bycho.safereturnhome.network

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class EventHttpClient(
    private val baseAddress: String,
    private val client: OkHttpClient = OkHttpClient()
) {
    suspend fun fetchDangerZones() = execute(
        Request.Builder().url(endpoint("danger-zones")).get().build()
    ).let(::parseDangerZoneList)

    suspend fun clearDangerZones() {
        execute(Request.Builder().url(endpoint("danger-zones")).delete().build())
    }

    suspend fun checkServer(): String = execute(
        Request.Builder().url(endpoint("")).get().build()
    )

    private fun endpoint(path: String): String {
        val address = baseAddress.trim().trimEnd('/')
        require(address.isNotBlank()) { "서버 주소가 비어 있습니다." }
        val httpAddress = when {
            address.startsWith("ws://") -> address.replaceFirst("ws://", "http://")
            address.startsWith("wss://") -> address.replaceFirst("wss://", "https://")
            address.startsWith("http://") || address.startsWith("https://") -> address
            else -> "http://$address"
        }.removeSuffix("/ws")
        return if (path.isBlank()) httpAddress else "$httpAddress/$path"
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

}
