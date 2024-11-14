package com.onesignal.user.internal

import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.mocks.MockHelper
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.internal.subscriptions.SubscriptionList
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify

class UserManagerTests : FunSpec({

    test("language is backed by the language context") {
        // Given
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        val languageContext = mockk<ILanguageContext>()

        val languageSlot = slot<String>()
        every { languageContext.language } returns "custom-language"
        every { languageContext.language = capture(languageSlot) } answers { }

        val userManager =
            UserManager(mockSubscriptionManager, MockHelper.identityModelStore(), MockHelper.propertiesModelStore(), languageContext)

        // When
        userManager.setLanguage("new-language")

        // Then
        languageSlot.captured shouldBe "new-language"
    }

    test("externalId is backed by the identity model") {
        // Given
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        val identityModelStore =
            MockHelper.identityModelStore {
                it.externalId = "my-external-id"
            }

        val userManager =
            UserManager(mockSubscriptionManager, identityModelStore, MockHelper.propertiesModelStore(), MockHelper.languageContext())

        // When
        val externalId = userManager.externalId

        // Then
        externalId shouldBe "my-external-id"
    }

    test("aliases are backed by the identity model") {
        // Given
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        val identityModelStore =
            MockHelper.identityModelStore {
                it["my-alias-key1"] = "my-alias-value1"
                it["my-alias-key2"] = "my-alias-value2"
            }

        val userManager =
            UserManager(mockSubscriptionManager, identityModelStore, MockHelper.propertiesModelStore(), MockHelper.languageContext())

        // When
        val alias1 = userManager.aliases["my-alias-key1"]
        val alias2 = userManager.aliases["my-alias-key2"]

        // add
        userManager.addAlias("my-alias-key3", "my-alias-value3")
        userManager.addAliases(mapOf("my-alias-key4" to "my-alias-value4", "my-alias-key5" to "my-alias-value5"))

        // add, then rename
        userManager.addAlias("my-alias-key6", "my-alias-value6")
        userManager.addAlias("my-alias-key6", "my-alias-value6-1")

        // remove
        userManager.removeAlias("my-alias-key1")

        // Then
        alias1 shouldBe "my-alias-value1"
        alias2 shouldBe "my-alias-value2"
        identityModelStore.model["my-alias-key3"] shouldBe "my-alias-value3"
        identityModelStore.model["my-alias-key4"] shouldBe "my-alias-value4"
        identityModelStore.model["my-alias-key5"] shouldBe "my-alias-value5"
        identityModelStore.model["my-alias-key6"] shouldBe "my-alias-value6-1"
        identityModelStore.model["my-alias-key1"] shouldBe null
    }

    test("tags are backed by the properties model") {
        // Given
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        val propertiesModelStore =
            MockHelper.propertiesModelStore {
                it.tags["my-tag-key1"] = "my-tag-value1"
                it.tags["my-tag-key2"] = "my-tag-value2"
                it.tags["my-tag-key3"] = "my-tag-value3"
                it.tags["my-tag-key4"] = "my-tag-value4"
            }

        val userManager =
            UserManager(mockSubscriptionManager, MockHelper.identityModelStore(), propertiesModelStore, MockHelper.languageContext())

        // When
        val tag1 = propertiesModelStore.model.tags["my-tag-key1"]
        val tag2 = propertiesModelStore.model.tags["my-tag-key2"]

        // add
        userManager.addTag("my-tag-key5", "my-tag-value5")
        userManager.addTags(mapOf("my-tag-key6" to "my-tag-value6", "my-tag-key7" to "my-tag-value7"))

        // add, then rename
        userManager.addTag("my-tag-key8", "my-tag-value8")
        userManager.addTag("my-tag-key8", "my-tag-value8-1")

        // remove
        userManager.removeTag("my-tag-key1")
        userManager.removeTags(listOf("my-tag-key2", "my-tag-key3"))

        // Then
        tag1 shouldBe "my-tag-value1"
        tag2 shouldBe "my-tag-value2"
        propertiesModelStore.model.tags["my-tag-key4"] shouldBe "my-tag-value4"
        propertiesModelStore.model.tags["my-tag-key5"] shouldBe "my-tag-value5"
        propertiesModelStore.model.tags["my-tag-key6"] shouldBe "my-tag-value6"
        propertiesModelStore.model.tags["my-tag-key7"] shouldBe "my-tag-value7"
        propertiesModelStore.model.tags["my-tag-key8"] shouldBe "my-tag-value8-1"
        propertiesModelStore.model.tags["my-tag-key1"] shouldBe null
        propertiesModelStore.model.tags["my-tag-key2"] shouldBe null
        propertiesModelStore.model.tags["my-tag-key3"] shouldBe null
    }

    test("getTags returns a copy of tags") {
        // Given
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        val propertiesModelStore =
            MockHelper.propertiesModelStore {
                it.tags["my-tag-key1"] = "my-tag-value1"
            }

        val userManager =
            UserManager(mockSubscriptionManager, MockHelper.identityModelStore(), propertiesModelStore, MockHelper.languageContext())

        // When
        val tagSnapshot1 = userManager.getTags()

        // Then
        tagSnapshot1.size shouldBe propertiesModelStore.model.tags.size
        tagSnapshot1["my-tag-key1"] shouldBe propertiesModelStore.model.tags["my-tag-key1"]

        // Modify
        userManager.addTag("my-tag-key2", "my-tag-value2")
        userManager.getTags().size shouldBe 2

        // Then
        tagSnapshot1.size shouldNotBe userManager.getTags().size
    }

    test("subscriptions are backed by the subscriptions manager") {
        // Given
        val subscriptionList = SubscriptionList(listOf(), UninitializedPushSubscription())
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        every { mockSubscriptionManager.subscriptions } returns subscriptionList
        every { mockSubscriptionManager.addEmailSubscription(any()) } just runs
        every { mockSubscriptionManager.removeEmailSubscription(any()) } just runs
        every { mockSubscriptionManager.addSmsSubscription(any()) } just runs
        every { mockSubscriptionManager.removeSmsSubscription(any()) } just runs

        val userManager =
            UserManager(
                mockSubscriptionManager,
                MockHelper.identityModelStore(),
                MockHelper.propertiesModelStore(),
                MockHelper.languageContext(),
            )

        // When
        val subscriptions = userManager.subscriptions
        userManager.addEmail("email@co.com")
        userManager.removeEmail("email@co.com")

        userManager.addSms("+15558675309")
        userManager.removeSms("+15558675309")

        // Then
        subscriptions shouldBe subscriptionList
        verify(exactly = 1) { mockSubscriptionManager.addEmailSubscription("email@co.com") }
        verify(exactly = 1) { mockSubscriptionManager.removeEmailSubscription("email@co.com") }
        verify(exactly = 1) { mockSubscriptionManager.addSmsSubscription("+15558675309") }
        verify(exactly = 1) { mockSubscriptionManager.removeSmsSubscription("+15558675309") }
    }
})
