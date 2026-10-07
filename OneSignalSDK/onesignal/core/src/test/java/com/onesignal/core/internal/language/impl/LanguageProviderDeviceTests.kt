package com.onesignal.core.internal.language.impl

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

class LanguageProviderDeviceTests : FunSpec({
    listOf(
        "zh-Hans-HK" to "zh-Hans",
        "zh-Hant-CN" to "zh-Hant",
        "zh-CN" to "zh-Hans",
        "zh-TW" to "zh-Hant",
        "zh" to "zh-Hans",
    ).forEach { (languageTag, expectedLanguage) ->
        test("$languageTag maps to $expectedLanguage") {
            val languageProvider = LanguageProviderDevice { Locale.forLanguageTag(languageTag) }

            languageProvider.language shouldBe expectedLanguage
        }
    }
})
