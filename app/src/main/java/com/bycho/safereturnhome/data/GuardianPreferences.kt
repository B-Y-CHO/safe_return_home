package com.bycho.safereturnhome.data

import android.content.Context

private const val SOS_PREFERENCES_NAME = "sos-preferences"
private const val SOS_GUARDIAN_PHONE_KEY = "guardian-phone"

class GuardianPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(
        SOS_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getPhoneNumber(): String {
        return preferences.getString(SOS_GUARDIAN_PHONE_KEY, "").orEmpty()
    }

    fun savePhoneNumber(phoneNumber: String) {
        preferences.edit()
            .putString(SOS_GUARDIAN_PHONE_KEY, normalizeGuardianPhoneNumber(phoneNumber))
            .apply()
    }
}
