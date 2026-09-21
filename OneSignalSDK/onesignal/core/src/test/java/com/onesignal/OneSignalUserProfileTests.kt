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
        profile.hasFields shouldBe false
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
        profile.hasFields shouldBe true
    }

    test("constructor copies tags so later mutation of the caller's map is not visible") {
        val tags = mutableMapOf("plan" to "pro")
        val profile = OneSignalUserProfile(tags = tags)

        tags["plan"] = "changed"

        profile.tags shouldBe mapOf("plan" to "pro")
    }

    test("toString redacts email and phone") {
        val profile = OneSignalUserProfile(email = "bob@example.com", phoneNumber = "+15555550100")

        profile.toString().contains("bob@example.com") shouldBe false
        profile.toString().contains("+15555550100") shouldBe false
        profile.toString().contains("<set>") shouldBe true
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

    test("blank email and phone do not count as fields") {
        OneSignalUserProfile(email = "  ", phoneNumber = "").hasFields shouldBe false
    }

    test("validationError is null for a valid profile") {
        OneSignalUserProfile(email = "bob@example.com", phoneNumber = "+15555550100").validationError().shouldBeNull()
        OneSignalUserProfile().validationError().shouldBeNull()
    }

    test("validationError rejects a malformed email") {
        OneSignalUserProfile(email = "not-an-email").validationError() shouldBe "Invalid email address"
    }

    test("validationError rejects a non-E.164 phone number") {
        OneSignalUserProfile(phoneNumber = "555-0100").validationError() shouldBe "Invalid phone number"
    }
})
