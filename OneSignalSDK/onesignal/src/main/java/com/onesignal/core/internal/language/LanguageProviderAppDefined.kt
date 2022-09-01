package com.onesignal.core.internal.language

import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceStores

class LanguageProviderAppDefined(
    private val preferences: IPreferencesService
) : ILanguageProvider {

    override var language: String
        get() = preferences.getString(PreferenceStores.ONESIGNAL, PREFS_OS_LANGUAGE, DEFAULT_LANGUAGE)!!
        set(language) {
            preferences.saveString(PreferenceStores.ONESIGNAL, PREFS_OS_LANGUAGE, language)
        }

    companion object {
        const val PREFS_OS_LANGUAGE = "PREFS_OS_LANGUAGE"
        private const val DEFAULT_LANGUAGE = "en"
    }
}
