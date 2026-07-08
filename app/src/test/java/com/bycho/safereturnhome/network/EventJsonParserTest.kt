package com.bycho.safereturnhome.network

import com.bycho.safereturnhome.data.DangerSeverity
import com.bycho.safereturnhome.data.DangerZoneType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventJsonParserTest {
    @Test
    fun parsesFastApiWebSocketEnvelope() {
        val zone = parseDangerEventMessage(
            """
            {
              "type":"danger_zone_created",
              "payload":{
                "id":"lamp_fault_001",
                "type":"LAMP_FAULT",
                "latitude":35.82912,
                "longitude":128.53244,
                "radiusMeters":40.0,
                "message":"lamp fault",
                "source":"UGV_01",
                "severity":"HIGH"
              }
            }
            """.trimIndent()
        )

        requireNotNull(zone)
        assertEquals(DangerZoneType.LAMP_FAULT, zone.type)
        assertEquals(DangerSeverity.HIGH, zone.severity)
        assertEquals("UGV_01", zone.source)
        assertEquals(40.0, zone.radiusMeters, 0.0)
    }

    @Test
    fun oldFieldsRemainCompatibleThroughAliasesAndDefaults() {
        val zone = parseDangerEventMessage(
            """{"type":"DANGER_EVENT","id":"old","lat":35.8,"lon":128.5,"radius_m":30,"message":"danger"}"""
        )

        requireNotNull(zone)
        assertEquals(DangerZoneType.DANGER_EVENT, zone.type)
        assertEquals(DangerSeverity.MEDIUM, zone.severity)
        assertEquals("UNKNOWN", zone.source)
    }

    @Test
    fun ignoresConnectionMessages() {
        assertNull(parseDangerEventMessage("""{"type":"connected"}"""))
    }

    @Test
    fun buildsEmulatorWebSocketUrl() {
        assertEquals(
            "ws://10.0.2.2:8000/ws",
            buildEventWebSocketUrl("http://10.0.2.2:8000")
        )
    }
}
