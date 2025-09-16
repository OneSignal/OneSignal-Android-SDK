package com.onesignal.common

import com.onesignal.core.BuildConfig
import java.util.regex.Pattern

object OneSignalUtils {
    /**
     * The version of this SDK. This is being formatted to ensure proper sorting when used in
     * User-Agent strings and other uses where lexicographical order matters.
     * Its being picked up from the Gradle build config field `SDK_VERSION`.
     * Also its calculated lazily once when first accessed and then cached after that.
     */
    val sdkVersion: String by lazy {
        formatVersion(BuildConfig.SDK_VERSION)
    }

    /**
     * Formats a version string to ensure proper lexicographical sorting.
     * For example, "1.2.3-beta" becomes "010203-beta", "1.20.3" becomes "010203".
     */
    internal fun formatVersion(version: String): String {
        val parts = version.split("-", limit = 2)
        val base = parts[0].split(".")
        val major = base.getOrNull(0)?.padStart(2, '0') ?: "00"
        val minor = base.getOrNull(1)?.padStart(2, '0') ?: "00"
        val patch = base.getOrNull(2)?.padStart(2, '0') ?: "00"
        val formatted = "$major$minor$patch"
        return if (parts.size > 1) formatted + "-" + parts[1] else formatted
    }

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
