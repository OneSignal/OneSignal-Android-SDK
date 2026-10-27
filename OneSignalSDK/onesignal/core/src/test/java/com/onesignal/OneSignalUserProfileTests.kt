package com.onesignal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class OneSignalUserProfileTests : FunSpec({

    test("Kotlin defaults are all empty") {
        val profile = OneSignalUserProfile()

        profile.email.shouldBeNull()
        profile.sms.shouldBeNull()
        profile.emailEnabled.shouldBeNull()
        profile.tags shouldBe emptyMap()
        profile.aliases shouldBe emptyMap()
        profile.hasFields shouldBe false
    }

    test("emailEnabled false is kept and true is distinct from unset") {
        val unsubscribed = OneSignalUserProfile(email = "bob@example.com", emailEnabled = false)
        val subscribed = OneSignalUserProfile(email = "bob@example.com", emailEnabled = true)
        val unset = OneSignalUserProfile(email = "bob@example.com")

        unsubscribed.emailEnabled shouldBe false
        subscribed.emailEnabled shouldBe true
        unset.emailEnabled.shouldBeNull()
        unsubscribed shouldBe OneSignalUserProfile(email = "bob@example.com", emailEnabled = false)
        (unsubscribed == unset) shouldBe false
        (unsubscribed == subscribed) shouldBe false
    }

    test("Java builder can store an email unsubscribed") {
        val profile =
            OneSignalUserProfile.builder()
                .setEmail("bob@example.com")
                .setEmailEnabled(false)
                .build()

        profile.email shouldBe "bob@example.com"
        profile.emailEnabled shouldBe false
    }

    test("Kotlin named args set only the fields that were passed") {
        val profile =
            OneSignalUserProfile(
                email = "bob@example.com",
                tags = mapOf("plan" to "pro"),
            )

        profile.email shouldBe "bob@example.com"
        profile.sms.shouldBeNull()
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

    test("tags and aliases cannot be mutated through a Java cast") {
        val profile = OneSignalUserProfile.builder().setTags(mapOf("plan" to "pro", "tier" to "gold")).setAliases(mapOf("crm" to "1", "erp" to "2")).build()

        shouldThrow<UnsupportedOperationException> { (profile.tags as MutableMap<String, String>).put("plan", "x") }
        shouldThrow<UnsupportedOperationException> { (profile.aliases as MutableMap<String, String>).clear() }
    }

    test("toString redacts email and sms") {
        val profile = OneSignalUserProfile(email = "bob@example.com", sms = "+15555550100")

        profile.toString().contains("bob@example.com") shouldBe false
        profile.toString().contains("+15555550100") shouldBe false
        profile.toString().contains("<set>") shouldBe true
    }

    test("Java builder can set email without an sms") {
        val profile =
            OneSignalUserProfile.builder()
                .setEmail("bob@example.com")
                .build()

        profile.email shouldBe "bob@example.com"
        profile.sms.shouldBeNull()
        profile.tags shouldBe emptyMap()
        profile.aliases shouldBe emptyMap()
    }

    test("Java builder can set sms without an email") {
        val profile =
            OneSignalUserProfile.Builder()
                .setSms("+15555550100")
                .setTags(mapOf("plan" to "pro"))
                .setAliases(mapOf("facebook" to "bob"))
                .build()

        profile.email.shouldBeNull()
        profile.sms shouldBe "+15555550100"
        profile.tags shouldBe mapOf("plan" to "pro")
        profile.aliases shouldBe mapOf("facebook" to "bob")
    }

    test("builder copies tags so later mutation of the caller's map is not visible") {
        val tags = mutableMapOf("plan" to "pro")
        val profile = OneSignalUserProfile.builder().setTags(tags).build()

        tags["plan"] = "changed"

        profile.tags shouldBe mapOf("plan" to "pro")
    }

    test("blank email and sms do not count as fields") {
        OneSignalUserProfile(email = "  ", sms = "").hasFields shouldBe false
    }

    test("validationError is null for a valid profile") {
        OneSignalUserProfile(email = "bob@example.com", sms = "+15555550100").validationError().shouldBeNull()
        OneSignalUserProfile().validationError().shouldBeNull()
    }

    test("validationError rejects a malformed email") {
        OneSignalUserProfile(email = "not-an-email").validationError() shouldBe "Invalid email address"
    }

    test("validationError rejects a non-E.164 sms") {
        OneSignalUserProfile(sms = "555-0100").validationError() shouldBe "Invalid SMS number"
    }

    test("validationError rejects an empty or NUL tag key and a null tag value") {
        val tagError = "Tag keys must be non-empty and values non-null"

        @Suppress("UNCHECKED_CAST")
        val nullValue = mapOf("level" to null) as Map<String, String>
        OneSignalUserProfile(tags = mapOf("" to "1")).validationError() shouldBe tagError
        OneSignalUserProfile(tags = mapOf("a\u0000b" to "1")).validationError() shouldBe tagError
        OneSignalUserProfile(tags = nullValue).validationError() shouldBe tagError
        OneSignalUserProfile(tags = mapOf("level" to "")).validationError().shouldBeNull()
    }

    test("validationError rejects an empty alias label or id") {
        val aliasError = "Alias labels and IDs must be non-empty"
        OneSignalUserProfile(aliases = mapOf("" to "id")).validationError() shouldBe aliasError
        OneSignalUserProfile(aliases = mapOf("crm" to "")).validationError() shouldBe aliasError
        OneSignalUserProfile(aliases = mapOf("crm" to "id")).validationError().shouldBeNull()
    }
})
