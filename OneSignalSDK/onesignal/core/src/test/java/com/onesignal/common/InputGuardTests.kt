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

    test("a null collection or map is missing") {
        isMissingAny(null, "removeTags: key") shouldBe true
        hasMissingEntries(null, "addTags", allowEmptyValue = true) shouldBe true
        hasMissingEntries(null, "addAliases", allowEmptyValue = false) shouldBe true
    }

    test("a collection holding a null element is missing") {
        // A Java or JNI caller can supply a list with a null in it, which the Kotlin type forbids.
        val keys = listOf("valid", null) as Collection<String>
        isMissingAny(keys, "removeTags: key") shouldBe true
    }

    test("a map holding a null key or value is missing") {
        val nullValue = mapOf("key" to null) as Map<String, String>
        val nullKey = mapOf(null to "value") as Map<String, String>

        // A null value is missing even where an empty value is allowed.
        hasMissingEntries(nullValue, "addTags", allowEmptyValue = true) shouldBe true
        hasMissingEntries(nullKey, "addTags", allowEmptyValue = true) shouldBe true
    }

    test("an empty value is allowed only when allowEmptyValue is set") {
        hasMissingEntries(mapOf("key" to ""), "addTags", allowEmptyValue = true) shouldBe false
        hasMissingEntries(mapOf("key" to ""), "addAliases", allowEmptyValue = false) shouldBe true
    }
})
