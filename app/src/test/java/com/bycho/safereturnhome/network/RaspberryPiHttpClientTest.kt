package com.bycho.safereturnhome.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RaspberryPiHttpClientTest {
    @Test
    fun statusEndpoint_appendsStatusPath() {
        assertEquals(
            "http://192.168.0.10:5000/status",
            statusEndpoint("http://192.168.0.10:5000")
        )
    }

    @Test
    fun statusEndpoint_removesTrailingSlashBeforeAppendingPath() {
        assertEquals(
            "http://192.168.0.10:5000/status",
            statusEndpoint("http://192.168.0.10:5000/")
        )
    }

    @Test
    fun parseServerStatusResponse_readsFlaskStatusAndMessage() {
        val result = parseServerStatusResponse(
            """{"status":"DANGER","message":"경고: 의심스러운 인물이 미행 중입니다!"}"""
        )

        assertEquals("DANGER", result.status)
        assertEquals("경고: 의심스러운 인물이 미행 중입니다!", result.message)
        assertNull(result.errorMessage)
    }

    @Test
    fun parseServerStatusResponse_reportsMissingStatus() {
        val result = parseServerStatusResponse("""{"message":"주변이 안전합니다."}""")

        assertNull(result.status)
        assertEquals("응답에 status 값이 없습니다.", result.errorMessage)
    }
}
