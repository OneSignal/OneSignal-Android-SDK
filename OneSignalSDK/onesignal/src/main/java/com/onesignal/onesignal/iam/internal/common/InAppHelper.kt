package com.onesignal.onesignal.iam.internal.common

import com.onesignal.onesignal.iam.internal.InAppMessage

internal object InAppHelper {
    private val PREFERRED_VARIANT_ORDER: List<String> = listOf("android", "app", "all")

    fun variantIdForMessage(message: InAppMessage): String? {
        // TODO: Language context stuff
//        val language: String = languageContext.getLanguage()
        var language = "en"
        for (variant in PREFERRED_VARIANT_ORDER) {
            if (!message.variants.containsKey(variant)) continue
            val variantMap = message.variants[variant]!!
            return if (variantMap.containsKey(language)) variantMap[language] else variantMap["default"]
        }
        return null
    }
}
