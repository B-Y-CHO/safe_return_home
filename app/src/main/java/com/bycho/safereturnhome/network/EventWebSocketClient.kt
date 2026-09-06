package com.bycho.safereturnhome.network

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class EventWebSocketClient(
    private val baseAddress: String,
    private val client: OkHttpClient = OkHttpClient()
) {
    fun observe(): Flow<DangerEventUpdate> = callbackFlow {
        val url = buildEventWebSocketUrl(baseAddress)
        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                trySend(DangerEventUpdate.Connected(url))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { parseEventMessage(text) }
                    .onSuccess { update ->
                        if (update != null) trySend(update)
                    }
                    .onFailure { trySend(DangerEventUpdate.Failed("이벤트 JSON 파싱 실패", it)) }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                onMessage(webSocket, bytes.utf8())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                trySend(DangerEventUpdate.Closed(code, reason))
                close()
            }

            override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                trySend(DangerEventUpdate.Failed(error.message ?: "WebSocket 연결 실패", error))
                close()
            }
        }
        val socket = client.newWebSocket(Request.Builder().url(url).build(), listener)
        awaitClose { socket.close(1000, "화면 종료") }
    }
}

fun buildEventWebSocketUrl(serverAddress: String): String {
    val address = serverAddress.trim().trimEnd('/')
    require(address.isNotBlank()) { "서버 주소가 비어 있습니다." }
    val webSocketAddress = when {
        address.startsWith("ws://") || address.startsWith("wss://") -> address
        address.startsWith("https://") -> address.replaceFirst("https://", "wss://")
        address.startsWith("http://") -> address.replaceFirst("http://", "ws://")
        else -> "ws://$address"
    }
    return if (webSocketAddress.endsWith("/ws")) webSocketAddress else "$webSocketAddress/ws"
}
