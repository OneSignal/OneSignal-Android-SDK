package com.onesignal.common

import java.util.regex.Pattern

object OneSignalUtils {
    /**
     * The version of this SDK.
     */
    const val SDK_VERSION: String = "050123"

    fun isValidEmail(email: String): Boolean {
        if (email.isEmpty()) {
            return false
        }

        val emRegex = "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@((\\[[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\])|(([a-zA-Z\\-0-9]+\\.)+[a-zA-Z]{2,}))$"
        val pattern: Pattern = Pattern.compile(emRegex)
        return pattern.matcher(email).matches()
    }

    fun isValidPhoneNumber(number: String): Boolean {
        if (number.isEmpty()) {
            return false
        }

        val emRegex = "^\\+?[1-9]\\d{1,14}\$"
        val pattern: Pattern = Pattern.compile(emRegex)
        return pattern.matcher(number).matches()
    }
}
