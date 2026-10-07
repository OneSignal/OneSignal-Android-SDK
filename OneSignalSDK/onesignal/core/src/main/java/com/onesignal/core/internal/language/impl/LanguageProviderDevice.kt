package com.onesignal.core.internal.language.impl

import java.util.Locale

internal class LanguageProviderDevice(
    private val localeProvider: () -> Locale = Locale::getDefault,
) {
    val language: String
        get() {
            val locale = localeProvider()
            return when (val language = locale.language) {
                HEBREW_INCORRECT -> HEBREW_CORRECTED
                INDONESIAN_INCORRECT -> INDONESIAN_CORRECTED
                YIDDISH_INCORRECT -> YIDDISH_CORRECTED
                CHINESE -> chineseLanguage(locale)
                else -> language
            }
        }

    private fun chineseLanguage(locale: Locale): String =
        when (locale.script) {
            SIMPLIFIED_CHINESE_SCRIPT -> SIMPLIFIED_CHINESE
            TRADITIONAL_CHINESE_SCRIPT -> TRADITIONAL_CHINESE
            else -> if (locale.country in TRADITIONAL_CHINESE_REGIONS) TRADITIONAL_CHINESE else SIMPLIFIED_CHINESE
        }

    companion object {
        private const val HEBREW_INCORRECT = "iw"
        private const val HEBREW_CORRECTED = "he"
        private const val INDONESIAN_INCORRECT = "in"
        private const val INDONESIAN_CORRECTED = "id"
        private const val YIDDISH_INCORRECT = "ji"
        private const val YIDDISH_CORRECTED = "yi"
        private const val CHINESE = "zh"
        private const val SIMPLIFIED_CHINESE_SCRIPT = "Hans"
        private const val TRADITIONAL_CHINESE_SCRIPT = "Hant"
        private const val SIMPLIFIED_CHINESE = "zh-Hans"
        private const val TRADITIONAL_CHINESE = "zh-Hant"
        private val TRADITIONAL_CHINESE_REGIONS = setOf("HK", "MO", "TW")
    }
}
