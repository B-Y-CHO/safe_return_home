package com.bycho.safereturnhome.network

import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class RaspberryWebSocketManager(
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    fun observe(serverAddress: String): Flow<RaspberryWebSocketEvent> = callbackFlow {
        val webSocketUrl = buildRaspberryWebSocketUrl(serverAddress)
        val request = Request.Builder().url(webSocketUrl).build()
        Log.d(LOG_TAG, "WebSocket 연결 시도: $webSocketUrl")

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(LOG_TAG, "WebSocket 연결 성공")
                val sent = webSocket.send(APP_CONNECTED_MESSAGE)
                if (sent) {
                    Log.d(LOG_TAG, "app_connected 메시지 전송 완료")
                } else {
                    Log.w(LOG_TAG, "app_connected 메시지를 전송하지 못했습니다")
                }
                trySend(RaspberryWebSocketEvent.Connected(webSocketUrl))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i(LOG_TAG, "서버 메시지 수신: $text")
                trySend(RaspberryWebSocketEvent.MessageReceived(text))
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(LOG_TAG, "WebSocket 연결 종료 중: code=$code, reason=$reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(LOG_TAG, "WebSocket 연결 종료: code=$code, reason=$reason")
                trySend(RaspberryWebSocketEvent.Closed(code, reason))
                close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val responseCode = response?.code?.let { ", HTTP $it" }.orEmpty()
                Log.e(LOG_TAG, "WebSocket 연결 실패$responseCode: ${t.message}", t)
                trySend(
                    RaspberryWebSocketEvent.Failed(
                        message = t.message ?: "알 수 없는 WebSocket 연결 오류",
                        cause = t
                    )
                )
                close()
            }
        }

        val webSocket = okHttpClient.newWebSocket(request, listener)
        awaitClose {
            webSocket.close(NORMAL_CLOSURE_STATUS, "앱 수신 화면 종료")
        }
    }

    companion object {
        const val DEFAULT_SERVER_ADDRESS =
            "ws://[2001:2d8:7e4f:1aa7:4876:31ff:fe5a:60f8]:8000/ws"
        private const val LOG_TAG = "RaspberryWebSocket"
        private const val APP_CONNECTED_MESSAGE = "{\"type\":\"app_connected\"}"
        private const val NORMAL_CLOSURE_STATUS = 1000
    }
}

sealed interface RaspberryWebSocketEvent {
    data class Connected(val url: String) : RaspberryWebSocketEvent
    data class MessageReceived(val text: String) : RaspberryWebSocketEvent
    data class Failed(val message: String, val cause: Throwable?) : RaspberryWebSocketEvent
    data class Closed(val code: Int, val reason: String) : RaspberryWebSocketEvent
}

fun buildRaspberryWebSocketUrl(serverAddress: String): String {
    val address = serverAddress.trim().trimEnd('/')
    require(address.isNotBlank()) { "WebSocket 서버 주소가 비어 있습니다" }
    val webSocketAddress = when {
        address.startsWith("ws://") || address.startsWith("wss://") -> address
        address.startsWith("https://") -> address.replaceFirst("https://", "wss://")
        address.startsWith("http://") -> address.replaceFirst("http://", "ws://")
        else -> "ws://$address"
    }
    return if (webSocketAddress.endsWith("/ws")) webSocketAddress else "$webSocketAddress/ws"
}
