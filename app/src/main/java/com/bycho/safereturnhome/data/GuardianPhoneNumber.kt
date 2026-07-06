package com.bycho.safereturnhome.data

private val KOREAN_MOBILE_PHONE_PATTERN = Regex("^01\\d{8,9}$")
private val ALLOWED_PHONE_INPUT_PATTERN = Regex("^[\\d\\s-]+$")

fun normalizeGuardianPhoneNumber(phoneNumber: String): String {
    return phoneNumber.filter(Char::isDigit)
}

fun guardianPhoneNumberError(phoneNumber: String): String? {
    val normalizedPhoneNumber = normalizeGuardianPhoneNumber(phoneNumber)
    return when {
        normalizedPhoneNumber.isBlank() -> "보호자 휴대폰 번호를 입력해주세요."
        !ALLOWED_PHONE_INPUT_PATTERN.matches(phoneNumber) ->
            "숫자, 하이픈, 공백만 입력해주세요."
        !KOREAN_MOBILE_PHONE_PATTERN.matches(normalizedPhoneNumber) ->
            "01로 시작하는 휴대폰 번호 10~11자리를 입력해주세요."
        else -> null
    }
}
