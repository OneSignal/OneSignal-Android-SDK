package com.onesignal.common

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InputGuardTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("null and empty strings are missing") {
        isMissing(null, "login: externalId") shouldBe true
        isMissing("", "login: externalId") shouldBe true
    }

    test("a normal string is not missing") {
        isMissing("user-1", "login: externalId") shouldBe false
    }

    test("a string containing a null byte is missing") {
        isMissing("\u0000: 1", "login: externalId") shouldBe true
        isMissing("abc\u0000", "addAlias: id") shouldBe true
        hasMissingEntries(mapOf("external_id" to "\u0000: 1"), "addAliases", allowEmptyValue = false) shouldBe true
    }
})
