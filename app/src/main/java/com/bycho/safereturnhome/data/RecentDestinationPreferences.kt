package com.bycho.safereturnhome.data

import android.content.Context

private const val DESTINATION_PREFERENCES_NAME = "destination-preferences"
private const val RECENT_DESTINATION_NAME_KEY = "recent-destination-name"
private const val RECENT_DESTINATION_ADDRESS_KEY = "recent-destination-address"
private const val RECENT_DESTINATION_LATITUDE_KEY = "recent-destination-latitude"
private const val RECENT_DESTINATION_LONGITUDE_KEY = "recent-destination-longitude"

data class RecentDestination(
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
) {
    fun label(): String {
        return if (address.isBlank()) {
            "최근 목적지: $name"
        } else {
            "최근 목적지: $name\n$address"
        }
    }
}

class RecentDestinationPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(
        DESTINATION_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getRecentDestination(): RecentDestination? {
        val name = preferences.getString(RECENT_DESTINATION_NAME_KEY, "").orEmpty()
        if (name.isBlank()) return null

        return RecentDestination(
            name = name,
            address = preferences.getString(RECENT_DESTINATION_ADDRESS_KEY, "").orEmpty(),
            latitude = preferences.getString(RECENT_DESTINATION_LATITUDE_KEY, null)
                ?.toDoubleOrNull()
                ?: return null,
            longitude = preferences.getString(RECENT_DESTINATION_LONGITUDE_KEY, null)
                ?.toDoubleOrNull()
                ?: return null
        )
    }

    fun saveRecentDestination(
        name: String,
        address: String,
        latitude: Double,
        longitude: Double
    ) {
        preferences.edit()
            .putString(RECENT_DESTINATION_NAME_KEY, name)
            .putString(RECENT_DESTINATION_ADDRESS_KEY, address)
            .putString(RECENT_DESTINATION_LATITUDE_KEY, latitude.toString())
            .putString(RECENT_DESTINATION_LONGITUDE_KEY, longitude.toString())
            .apply()
    }
}
