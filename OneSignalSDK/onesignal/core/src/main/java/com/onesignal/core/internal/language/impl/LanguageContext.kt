package com.onesignal.core.internal.language.impl

import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.user.internal.properties.PropertiesModelStore

internal class LanguageContext(
    private val _propertiesModelStore: PropertiesModelStore,
) : ILanguageContext {
    private var _deviceLanguageProvider = LanguageProviderDevice()

    override var language: String
        get() = _propertiesModelStore.model.language ?: _deviceLanguageProvider.language
        set(value) {
            _propertiesModelStore.model.language = value
        }
}
