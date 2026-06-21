package com.bycho.safereturnhome.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuardianPhoneNumberTest {
    @Test
    fun normalizeGuardianPhoneNumber_removesSeparators() {
        assertEquals("01012345678", normalizeGuardianPhoneNumber("010-1234 5678"))
    }

    @Test
    fun guardianPhoneNumberError_acceptsValidMobileNumbers() {
        assertNull(guardianPhoneNumberError("010-1234-5678"))
        assertNull(guardianPhoneNumberError("0111234567"))
    }

    @Test
    fun guardianPhoneNumberError_rejectsInvalidNumbers() {
        assertEquals(
            "01로 시작하는 휴대폰 번호 10~11자리를 입력해주세요.",
            guardianPhoneNumberError("0212345678")
        )
        assertEquals(
            "숫자, 하이픈, 공백만 입력해주세요.",
            guardianPhoneNumberError("010-1234-5678abc")
        )
    }
}
