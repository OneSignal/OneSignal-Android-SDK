package com.onesignal.core.internal.language

import java.util.Locale

internal class LanguageProviderDevice : ILanguageProvider {
    override val language: String
        get() {
            return when (val language = Locale.getDefault().language) {
                HEBREW_INCORRECT -> HEBREW_CORRECTED
                INDONESIAN_INCORRECT -> INDONESIAN_CORRECTED
                YIDDISH_INCORRECT -> YIDDISH_CORRECTED
                CHINESE -> language + "-" + Locale.getDefault().country
                else -> language
            }
        }

    companion object {
        private const val HEBREW_INCORRECT = "iw"
        private const val HEBREW_CORRECTED = "he"
        private const val INDONESIAN_INCORRECT = "in"
        private const val INDONESIAN_CORRECTED = "id"
        private const val YIDDISH_INCORRECT = "ji"
        private const val YIDDISH_CORRECTED = "yi"
        private const val CHINESE = "zh"
    }
}
