package com.bycho.safereturnhome.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DangerZoneParserTest {
    @Test
    fun parsesDangerZoneMessage() {
        val result = parseDangerZoneMessage(
            """
            {
              "type": "danger_zone",
              "id": "test_danger_001",
              "lat": 36.1416,
              "lon": 128.4082,
              "radius_m": 50,
              "message": "테스트 위험지점"
            }
            """.trimIndent()
        )

        requireNotNull(result)
        assertEquals("test_danger_001", result.id)
        assertEquals(36.1416, result.latitude, 0.0)
        assertEquals(128.4082, result.longitude, 0.0)
        assertEquals(50.0, result.radiusMeters, 0.0)
        assertEquals("테스트 위험지점", result.message)
    }

    @Test
    fun ignoresNonDangerMessage() {
        assertNull(parseDangerZoneMessage("""{"status":"SAFE"}"""))
    }

    @Test
    fun rejectsInvalidDangerRadius() {
        assertThrows(IllegalArgumentException::class.java) {
            parseDangerZoneMessage(
                """{"type":"danger_zone","id":"bad","lat":36.1,"lon":128.4,"radius_m":0}"""
            )
        }
    }
}
