package com.bycho.safereturnhome.data

import java.net.URI

fun normalizeServerAddress(serverAddress: String): String {
    return serverAddress.trim().trimEnd('/')
}

fun serverAddressError(serverAddress: String): String? {
    val normalizedAddress = normalizeServerAddress(serverAddress)
    if (normalizedAddress.isBlank()) {
        return "서버 주소를 입력하세요."
    }

    val uri = runCatching { URI(normalizedAddress) }.getOrNull()
        ?: return "올바른 서버 주소를 입력하세요."

    val allowedSchemes = setOf("http", "https", "ws", "wss")
    if (uri.scheme !in allowedSchemes) {
        return "올바른 주소 형식(http, https, ws, wss)을 입력하세요."
    }
    if (uri.host.isNullOrBlank()) {
        return "IP 주소 또는 호스트 이름을 입력하세요."
    }
    if (uri.port != -1 && uri.port !in 1..65535) {
        return "포트 번호는 1부터 65535까지 입력하세요."
    }
    if (uri.rawPath?.let { it.isNotBlank() && it != "/" && it != "/ws" } == true ||
        uri.rawQuery != null ||
        uri.rawFragment != null
    ) {
        return "경로 없이 서버 기본 주소나 /ws 까지만 입력하세요."
    }

    return null
}
