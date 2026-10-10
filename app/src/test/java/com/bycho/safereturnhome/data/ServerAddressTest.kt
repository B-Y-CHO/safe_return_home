package com.bycho.safereturnhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {
    @Test
    fun normalizeServerAddress_removesWhitespaceAndTrailingSlash() {
        assertEquals(
            "http://192.168.0.10:8080",
            normalizeServerAddress(" http://192.168.0.10:8080/ ")
        )
    }

    @Test
    fun serverAddressError_acceptsHttpIpAddressWithPort() {
        assertNull(serverAddressError("http://192.168.0.10:8080"))
    }

    @Test
    fun serverAddressError_acceptsWebSocketAndHttpsAddresses() {
        assertNull(serverAddressError("https://192.168.0.10:8080"))
        assertNull(serverAddressError("ws://192.168.0.10:8080"))
        assertNull(serverAddressError("wss://192.168.0.10:8080"))
    }

    @Test
    fun serverAddressError_rejectsAddressWithPath() {
        assertEquals(
            "경로 없이 서버 기본 주소나 /ws 까지만 입력하세요.",
            serverAddressError("http://192.168.0.10:8080/health")
        )
    }

    @Test
    fun serverAddressError_rejectsInvalidPort() {
        assertEquals(
            "포트 번호는 1부터 65535까지 입력하세요.",
            serverAddressError("http://192.168.0.10:65536")
        )
    }
}
