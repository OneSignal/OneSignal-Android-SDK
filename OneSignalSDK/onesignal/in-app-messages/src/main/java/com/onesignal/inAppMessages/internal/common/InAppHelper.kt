package com.onesignal.inAppMessages.internal.common

import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.inAppMessages.internal.InAppMessage

internal object InAppHelper {
    private val PREFERRED_VARIANT_ORDER: List<String> = listOf("android", "app", "all")

    fun variantIdForMessage(
        message: InAppMessage,
        languageContext: ILanguageContext,
    ): String? {
        val language: String = languageContext.language
        for (variant in PREFERRED_VARIANT_ORDER) {
            if (!message.variants.containsKey(variant)) continue
            val variantMap = message.variants[variant]!!
            return if (variantMap.containsKey(language)) variantMap[language] else variantMap["default"]
        }
        return null
    }
}
