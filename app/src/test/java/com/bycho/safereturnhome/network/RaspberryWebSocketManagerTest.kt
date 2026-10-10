package com.bycho.safereturnhome.network

import org.junit.Assert.assertEquals
import org.junit.Test

class RaspberryWebSocketManagerTest {
    @Test
    fun keepsBracketedIpv6WebSocketUrl() {
        assertEquals(
            "ws://[2001:2d8:7e4f:1aa7:4876:31ff:fe5a:60f8]:8000/ws",
            buildRaspberryWebSocketUrl(
                "ws://[2001:2d8:7e4f:1aa7:4876:31ff:fe5a:60f8]:8000/ws"
            )
        )
    }

    @Test
    fun appendsWebSocketPathToBaseAddress() {
        assertEquals(
            "ws://[2001:db8::1]:8000/ws",
            buildRaspberryWebSocketUrl("[2001:db8::1]:8000")
        )
    }

    @Test
    fun convertsHttpSchemeForWebSocketConnection() {
        assertEquals(
            "ws://192.168.0.10:8000/ws",
            buildRaspberryWebSocketUrl("http://192.168.0.10:8000")
        )
    }
}
