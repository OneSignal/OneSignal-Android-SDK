package com.onesignal

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class OneSignalUserProfileTests : FunSpec({

    test("Kotlin defaults are all empty") {
        val profile = OneSignalUserProfile()

        profile.email.shouldBeNull()
        profile.phoneNumber.shouldBeNull()
        profile.tags shouldBe emptyMap()
        profile.aliases shouldBe emptyMap()
    }

    test("Kotlin named args set only the fields that were passed") {
        val profile =
            OneSignalUserProfile(
                email = "bob@example.com",
                tags = mapOf("plan" to "pro"),
            )

        profile.email shouldBe "bob@example.com"
        profile.phoneNumber.shouldBeNull()
        profile.tags shouldBe mapOf("plan" to "pro")
        profile.aliases shouldBe emptyMap()
    }

    test("Java builder can set email without a phone number") {
        val profile =
            OneSignalUserProfile.builder()
                .setEmail("bob@example.com")
                .build()

        profile.email shouldBe "bob@example.com"
        profile.phoneNumber.shouldBeNull()
        profile.tags shouldBe emptyMap()
        profile.aliases shouldBe emptyMap()
    }

    test("Java builder can set a phone number without an email") {
        val profile =
            OneSignalUserProfile.Builder()
                .setPhoneNumber("+15555550100")
                .setTags(mapOf("plan" to "pro"))
                .setAliases(mapOf("facebook" to "bob"))
                .build()

        profile.email.shouldBeNull()
        profile.phoneNumber shouldBe "+15555550100"
        profile.tags shouldBe mapOf("plan" to "pro")
        profile.aliases shouldBe mapOf("facebook" to "bob")
    }

    test("builder copies tags so later mutation of the caller's map is not visible") {
        val tags = mutableMapOf("plan" to "pro")
        val profile = OneSignalUserProfile.builder().setTags(tags).build()

        tags["plan"] = "changed"

        profile.tags shouldBe mapOf("plan" to "pro")
    }
})
