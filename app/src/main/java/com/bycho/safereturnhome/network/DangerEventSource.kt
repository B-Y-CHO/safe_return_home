package com.bycho.safereturnhome.network

import com.bycho.safereturnhome.data.DangerZone
import kotlinx.coroutines.flow.Flow

interface DangerEventSource {
    fun observeDangerZones(): Flow<DangerEventUpdate>
    suspend fun fetchDangerZones(): List<DangerZone>
    suspend fun clearDangerZones()
}

sealed interface DangerEventUpdate {
    data class Connected(val url: String) : DangerEventUpdate
    data class DangerZoneCreated(val dangerZone: DangerZone) : DangerEventUpdate
    data object DangerZonesCleared : DangerEventUpdate
    data class Failed(val message: String, val cause: Throwable? = null) : DangerEventUpdate
    data class Closed(val code: Int, val reason: String) : DangerEventUpdate
}
