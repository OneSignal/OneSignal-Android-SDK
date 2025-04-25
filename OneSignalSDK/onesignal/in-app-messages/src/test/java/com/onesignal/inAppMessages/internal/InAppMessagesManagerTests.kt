package com.onesignal.inAppMessages.internal

import com.onesignal.common.consistency.models.IConsistencyManager
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.inAppMessages.internal.backend.IInAppBackendService
import com.onesignal.inAppMessages.internal.display.IInAppDisplayer
import com.onesignal.inAppMessages.internal.lifecycle.IInAppLifecycleService
import com.onesignal.inAppMessages.internal.preferences.IInAppPreferencesController
import com.onesignal.inAppMessages.internal.repositories.IInAppRepository
import com.onesignal.inAppMessages.internal.state.InAppStateService
import com.onesignal.inAppMessages.internal.triggers.ITriggerController
import com.onesignal.mocks.MockHelper
import com.onesignal.session.internal.influence.IInfluenceManager
import com.onesignal.session.internal.outcomes.IOutcomeEventsController
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.user.IUserManager
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

class InAppMessagesManagerTests : FunSpec({

    test("triggers are backed by the trigger model store") {
        // Given
        val mockTriggerModelStore = mockk<com.onesignal.inAppMessages.internal.triggers.TriggerModelStore>()
        val triggerModelSlots = mutableListOf<com.onesignal.inAppMessages.internal.triggers.TriggerModel>()
        every { mockTriggerModelStore.get(any()) } returns null
        every { mockTriggerModelStore.add(capture(triggerModelSlots)) } answers {}
        every { mockTriggerModelStore.remove(any()) } just runs
        every { mockTriggerModelStore.clear() } just runs

        val iamManager =
            InAppMessagesManager(
                MockHelper.applicationService(),
                mockk<ISessionService>(),
                mockk<IInfluenceManager>(),
                mockk<ConfigModelStore>(),
                mockk<IUserManager>(),
                MockHelper.identityModelStore(),
                mockk<ISubscriptionManager>(),
                mockk<IOutcomeEventsController>(),
                mockk<InAppStateService>(),
                mockk<IInAppPreferencesController>(),
                mockk<IInAppRepository>(),
                mockk<IInAppBackendService>(),
                mockk<ITriggerController>(),
                mockTriggerModelStore,
                mockk<IInAppDisplayer>(),
                mockk<IInAppLifecycleService>(),
                MockHelper.languageContext(),
                MockHelper.time(1000),
                mockk<IConsistencyManager>(),
            )

        // When
        iamManager.addTrigger("trigger-key1", "trigger-value1")
        iamManager.addTriggers(mapOf("trigger-key2" to "trigger-value2", "trigger-key3" to "trigger-value3"))
        iamManager.removeTrigger("trigger-key4")
        iamManager.removeTriggers(listOf("trigger-key5", "trigger-key6"))
        iamManager.clearTriggers()

        // Then
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
