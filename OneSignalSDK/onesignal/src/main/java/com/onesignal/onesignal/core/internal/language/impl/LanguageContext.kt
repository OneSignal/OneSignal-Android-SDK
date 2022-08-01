package com.onesignal.onesignal.core.internal.language.impl

import com.onesignal.onesignal.core.internal.language.ILanguageProvider
import com.onesignal.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.onesignal.core.internal.preferences.PreferenceStores

/*
The Interface implements a getter and setter for the Language Provider.
It defaults to the device defined Language unless a language override is set.
 */
class LanguageContext(
    private val _preferences: IPreferencesService) {

    private var strategy: ILanguageProvider? = null

    init {
        instance = this
        val languageAppDefined = _preferences.getString(PreferenceStores.ONESIGNAL,
            LanguageProviderAppDefined.PREFS_OS_LANGUAGE, null)

        strategy = if (languageAppDefined != null) {
            LanguageProviderAppDefined(_preferences)
        } else {
            LanguageProviderDevice()
        }
    }

    fun setStrategy(strategy: ILanguageProvider?) {
        this.strategy = strategy
    }

    val language: String
        get() = strategy!!.language

    companion object {
        var instance: LanguageContext? = null
            private set
    }
}