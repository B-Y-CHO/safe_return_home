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
    fun serverAddressError_rejectsHttpsAddress() {
        assertEquals(
            "HTTP 주소만 입력할 수 있습니다.",
            serverAddressError("https://192.168.0.10:8080")
        )
    }

    @Test
    fun serverAddressError_rejectsAddressWithPath() {
        assertEquals(
            "경로 없이 서버 기본 주소만 입력하세요.",
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
