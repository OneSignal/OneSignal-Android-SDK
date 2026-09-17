package com.onesignal.core.internal.language.impl

import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LanguageContextTests : FunSpec({
    test("setting a language stores the explicit value") {
        val propertiesModelStore = MockHelper.propertiesModelStore()
        val languageContext = LanguageContext(propertiesModelStore)

        languageContext.language = "de"

        propertiesModelStore.model.language shouldBe "de"
        languageContext.language shouldBe "de"
    }

    test("setting an empty language clears the override and uses the device language") {
        val propertiesModelStore =
            MockHelper.propertiesModelStore {
                it.language = "de"
            }
        val languageContext = LanguageContext(propertiesModelStore)

        languageContext.language = ""

        propertiesModelStore.model.language shouldBe null
    }
})
