package com.bycho.safereturnhome.network

class FastApiDangerEventSource(
    serverAddress: String
) : DangerEventSource {
    private val httpClient = EventHttpClient(serverAddress)
    private val webSocketClient = EventWebSocketClient(serverAddress)

    override fun observeDangerZones() = webSocketClient.observe()
    override suspend fun fetchDangerZones() = httpClient.fetchDangerZones()
    override suspend fun requestUavEscort(latitude: Double, longitude: Double) =
        httpClient.requestUavEscort(latitude, longitude)
}
