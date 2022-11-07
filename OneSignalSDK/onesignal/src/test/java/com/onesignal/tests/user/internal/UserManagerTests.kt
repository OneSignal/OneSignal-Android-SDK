package com.onesignal.tests.user.internal

import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.tests.core.mocks.MockHelper
import com.onesignal.user.internal.UserManager
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.internal.triggers.TriggerModel
import com.onesignal.user.internal.triggers.TriggerModelStore
import com.onesignal.user.subscriptions.SubscriptionList
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class UserManagerTests : FunSpec({

    test("language is backed by the language context") {
        /* Given */
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        val mockTriggerModelStore = mockk<TriggerModelStore>()
        val languageContext = mockk<ILanguageContext>()

        val languageSlot = slot<String>()
        every { languageContext.language } returns "custom-language"
        every { languageContext.language = capture(languageSlot) } answers { }

        val userManager = UserManager(mockSubscriptionManager, MockHelper.identityModelStore(), MockHelper.propertiesModelStore(), mockTriggerModelStore, languageContext)

        /* When */
        val language = userManager.language
        userManager.language = "new-language"

        /* Then */
        language shouldBe "custom-language"
        languageSlot.captured shouldBe "new-language"
    }

    test("externalId is backed by the identity model") {
        /* Given */
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        val mockTriggerModelStore = mockk<TriggerModelStore>()
        val identityModelStore = MockHelper.identityModelStore {
            it.externalId = "my-external-id"
        }

        val userManager = UserManager(mockSubscriptionManager, identityModelStore, MockHelper.propertiesModelStore(), mockTriggerModelStore, MockHelper.languageContext())

        /* When */
        val externalId = userManager.externalId

        /* Then */
        externalId shouldBe "my-external-id"
    }

    test("aliases are backed by the identity model") {
        /* Given */
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        val mockTriggerModelStore = mockk<TriggerModelStore>()
        val identityModelStore = MockHelper.identityModelStore {
            it["my-alias-key1"] = "my-alias-value1"
            it["my-alias-key2"] = "my-alias-value2"
        }

        val userManager = UserManager(mockSubscriptionManager, identityModelStore, MockHelper.propertiesModelStore(), mockTriggerModelStore, MockHelper.languageContext())

        /* When */
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

        /* Then */
        alias1 shouldBe "my-alias-value1"
        alias2 shouldBe "my-alias-value2"
        identityModelStore.model["my-alias-key3"] shouldBe "my-alias-value3"
        identityModelStore.model["my-alias-key4"] shouldBe "my-alias-value4"
        identityModelStore.model["my-alias-key5"] shouldBe "my-alias-value5"
        identityModelStore.model["my-alias-key6"] shouldBe "my-alias-value6-1"
        identityModelStore.model["my-alias-key1"] shouldBe null
    }

    test("tags are backed by the properties model") {
        /* Given */
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        val mockTriggerModelStore = mockk<TriggerModelStore>()
        val propertiesModelStore = MockHelper.propertiesModelStore() {
            it.tags["my-tag-key1"] = "my-tag-value1"
            it.tags["my-tag-key2"] = "my-tag-value2"
            it.tags["my-tag-key3"] = "my-tag-value3"
            it.tags["my-tag-key4"] = "my-tag-value4"
        }

        val userManager = UserManager(mockSubscriptionManager, MockHelper.identityModelStore(), propertiesModelStore, mockTriggerModelStore, MockHelper.languageContext())

        /* When */
        val tag1 = userManager.tags["my-tag-key1"]
        val tag2 = userManager.tags["my-tag-key2"]

        // add
        userManager.setTag("my-tag-key5", "my-tag-value5")
        userManager.setTags(mapOf("my-tag-key6" to "my-tag-value6", "my-tag-key7" to "my-tag-value7"))

        // add, then rename
        userManager.setTag("my-tag-key8", "my-tag-value8")
        userManager.setTag("my-tag-key8", "my-tag-value8-1")

        // remove
        userManager.removeTag("my-tag-key1")
        userManager.removeTags(listOf("my-tag-key2", "my-tag-key3"))

        /* Then */
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

    test("subscriptions are backed by the subscriptions manager") {
        /* Given */
        val subscriptionList = SubscriptionList(listOf())
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        every { mockSubscriptionManager.subscriptions } returns subscriptionList
        every { mockSubscriptionManager.addEmailSubscription(any()) } just runs
        every { mockSubscriptionManager.removeEmailSubscription(any()) } just runs
        every { mockSubscriptionManager.addSmsSubscription(any()) } just runs
        every { mockSubscriptionManager.removeSmsSubscription(any()) } just runs

        val mockTriggerModelStore = mockk<TriggerModelStore>()

        val userManager = UserManager(mockSubscriptionManager, MockHelper.identityModelStore(), MockHelper.propertiesModelStore(), mockTriggerModelStore, MockHelper.languageContext())

        /* When */
        val subscriptions = userManager.subscriptions
        userManager.addEmailSubscription("email@co.com")
        userManager.removeEmailSubscription("email@co.com")

        userManager.addSmsSubscription("+15558675309")
        userManager.removeSmsSubscription("+15558675309")

        /* Then */
        subscriptions shouldBe subscriptionList
        verify(exactly = 1) { mockSubscriptionManager.addEmailSubscription("email@co.com") }
        verify(exactly = 1) { mockSubscriptionManager.removeEmailSubscription("email@co.com") }
        verify(exactly = 1) { mockSubscriptionManager.addSmsSubscription("+15558675309") }
        verify(exactly = 1) { mockSubscriptionManager.removeSmsSubscription("+15558675309") }
    }

    test("triggers are backed by the trigger model store") {
        /* Given */
        val mockSubscriptionManager = mockk<ISubscriptionManager>()
        val mockTriggerModelStore = mockk<TriggerModelStore>()
        val triggerModelSlots = mutableListOf<TriggerModel>()
        every { mockTriggerModelStore.get(any()) } returns null
        every { mockTriggerModelStore.add(capture(triggerModelSlots)) } answers {}
        every { mockTriggerModelStore.remove(any()) } just runs
        every { mockTriggerModelStore.clear() } just runs

        val userManager = UserManager(mockSubscriptionManager, MockHelper.identityModelStore(), MockHelper.propertiesModelStore(), mockTriggerModelStore, MockHelper.languageContext())

        /* When */
        userManager.setTrigger("trigger-key1", "trigger-value1")
        userManager.setTriggers(mapOf("trigger-key2" to "trigger-value2", "trigger-key3" to "trigger-value3"))
        userManager.removeTrigger("trigger-key4")
        userManager.removeTriggers(listOf("trigger-key5", "trigger-key6"))
        userManager.clearTriggers()

        /* Then */
        triggerModelSlots[0].key shouldBe "trigger-key1"
        triggerModelSlots[0].value shouldBe "trigger-value1"
        triggerModelSlots[1].key shouldBe "trigger-key2"
        triggerModelSlots[1].value shouldBe "trigger-value2"
        triggerModelSlots[2].key shouldBe "trigger-key3"
        triggerModelSlots[2].value shouldBe "trigger-value3"

        verify(exactly = 1) { mockTriggerModelStore.remove("trigger-key4") }
        verify(exactly = 1) { mockTriggerModelStore.remove("trigger-key5") }
        verify(exactly = 1) { mockTriggerModelStore.remove("trigger-key6") }
        verify(exactly = 1) { mockTriggerModelStore.clear() }
    }
})
