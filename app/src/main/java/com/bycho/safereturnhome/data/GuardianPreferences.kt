package com.bycho.safereturnhome.data

import android.content.Context

private const val SOS_PREFERENCES_NAME = "sos-preferences"
private const val SOS_GUARDIAN_PHONE_KEY = "guardian-phone"
private const val SOS_GUARDIAN_PHONE_VERIFIED_KEY = "guardian-phone-verified"
private const val SOS_GUARDIAN_SMS_PHONE_KEY = "guardian-sms-phone"

class GuardianPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(
        SOS_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getPhoneNumber(): String {
        return preferences.getString(SOS_GUARDIAN_PHONE_KEY, "").orEmpty()
    }

    fun getSmsPhoneNumber(): String {
        return preferences.getString(SOS_GUARDIAN_SMS_PHONE_KEY, "").orEmpty()
    }

    fun getSosPhoneNumber(): String {
        return getSmsPhoneNumber().ifBlank { getPhoneNumber() }
    }

    fun savePhoneNumber(phoneNumber: String) {
        preferences.edit()
            .putString(SOS_GUARDIAN_PHONE_KEY, normalizeGuardianPhoneNumber(phoneNumber))
            .putBoolean(SOS_GUARDIAN_PHONE_VERIFIED_KEY, false)
            .apply()
    }

    fun saveVerifiedPhoneNumber(phoneNumber: String) {
        preferences.edit()
            .putString(SOS_GUARDIAN_PHONE_KEY, normalizeGuardianPhoneNumber(phoneNumber))
            .putBoolean(SOS_GUARDIAN_PHONE_VERIFIED_KEY, true)
            .apply()
    }

    fun saveSmsPhoneNumber(phoneNumber: String) {
        preferences.edit()
            .putString(SOS_GUARDIAN_SMS_PHONE_KEY, normalizeGuardianPhoneNumber(phoneNumber))
            .apply()
    }

    fun isPhoneNumberVerified(): Boolean {
        return getPhoneNumber().isNotBlank() &&
            preferences.getBoolean(SOS_GUARDIAN_PHONE_VERIFIED_KEY, false)
    }
}
