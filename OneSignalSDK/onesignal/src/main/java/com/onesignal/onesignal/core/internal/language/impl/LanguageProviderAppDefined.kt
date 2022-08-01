package com.onesignal.onesignal.core.internal.language.impl

import com.onesignal.onesignal.core.internal.language.ILanguageProvider
import com.onesignal.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.onesignal.core.internal.preferences.PreferenceStores

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