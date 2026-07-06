package com.bycho.safereturnhome.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val SERVER_PREFERENCES_NAME = "server-preferences"
private const val SERVER_ADDRESS_KEY = "server-address"

class ServerPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(
        SERVER_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getServerAddress(): String {
        return preferences.getString(SERVER_ADDRESS_KEY, "").orEmpty()
    }

    fun saveServerAddress(serverAddress: String) {
        preferences.edit()
            .putString(SERVER_ADDRESS_KEY, normalizeServerAddress(serverAddress))
            .apply()
    }

    fun observeServerAddress(): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == SERVER_ADDRESS_KEY) trySend(getServerAddress())
        }
        trySend(getServerAddress())
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            preferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
}
