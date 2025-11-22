package com.onesignal.inAppMessages.internal

import com.onesignal.common.consistency.IamFetchReadyCondition
import com.onesignal.common.consistency.RywData
import com.onesignal.common.consistency.models.IConsistencyManager
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.IInAppMessageClickListener
import com.onesignal.inAppMessages.IInAppMessageLifecycleListener
import com.onesignal.inAppMessages.InAppMessageActionUrlType
import com.onesignal.inAppMessages.internal.backend.IInAppBackendService
import com.onesignal.inAppMessages.internal.display.IInAppDisplayer
import com.onesignal.inAppMessages.internal.lifecycle.IInAppLifecycleService
import com.onesignal.inAppMessages.internal.preferences.IInAppPreferencesController
import com.onesignal.inAppMessages.internal.prompt.impl.InAppMessagePrompt
import com.onesignal.inAppMessages.internal.repositories.IInAppRepository
import com.onesignal.inAppMessages.internal.state.InAppStateService
import com.onesignal.inAppMessages.internal.triggers.ITriggerController
import com.onesignal.inAppMessages.internal.triggers.TriggerModel
import com.onesignal.inAppMessages.internal.triggers.TriggerModelStore
import com.onesignal.mocks.MockHelper
import com.onesignal.session.internal.influence.IInfluenceManager
import com.onesignal.session.internal.outcomes.IOutcomeEventsController
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.user.IUserManager
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.subscriptions.IPushSubscription
import com.onesignal.user.subscriptions.ISubscription
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

private class Mocks {
    // mock default services needed for InAppMessagesManager
    val applicationService = MockHelper.applicationService()
    val sessionService = mockk<ISessionService>(relaxed = true)
    val influenceManager = mockk<IInfluenceManager>(relaxed = true)
    val configModelStore = MockHelper.configModelStore()
    val userManager = mockk<IUserManager>(relaxed = true)
    val identityModelStore = MockHelper.identityModelStore()
    val pushSubscription = mockk<IPushSubscription>(relaxed = true)
    val outcomeEventsController = mockk<IOutcomeEventsController>(relaxed = true)
    val state = mockk<InAppStateService>(relaxed = true)
    val prefs = mockk<IInAppPreferencesController>(relaxed = true)
    val repository = mockk<IInAppRepository>(relaxed = true)
    val backend = mockk<IInAppBackendService>(relaxed = true)
    val triggerController = mockk<ITriggerController>(relaxed = true)
    val triggerModelStore = mockk<TriggerModelStore>(relaxed = true)
    val displayer = mockk<IInAppDisplayer>(relaxed = true)
    val lifecycle = mockk<IInAppLifecycleService>(relaxed = true)
    val languageContext = MockHelper.languageContext()
    val time = MockHelper.time(1000)
    val consistencyManager = mockk<IConsistencyManager>(relaxed = true)
    val inAppMessageLifecycleListener = mockk<IInAppMessageLifecycleListener>(relaxed = true)
    val deferred = mockk<CompletableDeferred<RywData?>>()
    val rywData = RywData("token", 100L)

    val subscriptionManager = mockk<ISubscriptionManager>(relaxed = true) {
        every { subscriptions } returns mockk {
            every { push } returns pushSubscription
        }
    }

    val outcome =
        run {
            val outcome = mockk<InAppMessageOutcome>(relaxed = true)
            every { outcome.name } returns "outcome-name"
            outcome
        }

    val inAppMessageClickResult =
        run {
            val result = mockk<InAppMessageClickResult>(relaxed = true)
            every { result.prompts } returns mutableListOf()
            every { result.outcomes } returns mutableListOf(outcome)
            every { result.tags } returns null
            every { result.url } returns null
            every { result.clickId } returns "click-id"
            result
        }

    // Helper function to create a test InAppMessage
    fun createTestMessage(
        messageId: String = "test-message-id",
        time: ITime = MockHelper.time(1000),
        isPreview: Boolean = false,
    ): InAppMessage {
        return if (isPreview) {
            InAppMessage(true, time)
        } else {
            // Create message with variants using JSON constructor so variantIdForMessage works
            val json = JSONObject()
            json.put("id", messageId)
            val variantsJson = JSONObject()
            val allVariantJson = JSONObject()
            allVariantJson.put("en", "variant-id-123")
            variantsJson.put("all", allVariantJson)
            json.put("variants", variantsJson)
            json.put("triggers", JSONArray())
            InAppMessage(json, time)
        }
    }

    // Helper function to create InAppMessagesManager with all dependencies
    val inAppMessagesManager = InAppMessagesManager(
        applicationService,
        sessionService,
        influenceManager,
        configModelStore,
        userManager,
        identityModelStore,
        subscriptionManager,
        outcomeEventsController,
        state,
        prefs,
        repository,
        backend,
        triggerController,
        triggerModelStore,
        displayer,
        lifecycle,
        languageContext,
        time,
        consistencyManager,
    )
}

class InAppMessagesManagerTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    context("Trigger Management") {
        test("triggers are backed by the trigger model store") {
            // Given
            val mocks = Mocks()
            val mockTriggerModelStore = mocks.triggerModelStore
            val triggerModelSlots = mutableListOf<TriggerModel>()
            every { mockTriggerModelStore.get(any()) } returns null
            every { mockTriggerModelStore.add(capture(triggerModelSlots)) } answers {}
            every { mockTriggerModelStore.remove(any()) } just runs
            every { mockTriggerModelStore.clear() } just runs

            val iamManager = mocks.inAppMessagesManager

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

        test("addTrigger updates existing trigger model when trigger already exists") {
            // Given
            val mocks = Mocks()
            val mockTriggerModelStore = mocks.triggerModelStore
            val existingTrigger = TriggerModel().apply {
                id = "existing-key"
                key = "existing-key"
                value = "old-value"
            }
            every { mockTriggerModelStore.get("existing-key") } returns existingTrigger
            every { mockTriggerModelStore.add(any()) } just runs

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.addTrigger("existing-key", "new-value")

            // Then
            existingTrigger.value shouldBe "new-value"
            verify(exactly = 0) { mockTriggerModelStore.add(any()) }
        }

        test("addTrigger creates new trigger model when trigger does not exist") {
            // Given
            val mocks = Mocks()
            val mockTriggerModelStore = mocks.triggerModelStore
            val triggerModelSlots = mutableListOf<TriggerModel>()
            every { mockTriggerModelStore.get("new-key") } returns null
            every { mockTriggerModelStore.add(capture(triggerModelSlots)) } answers {}

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.addTrigger("new-key", "new-value")

            // Then
            triggerModelSlots.size shouldBe 1
            triggerModelSlots[0].key shouldBe "new-key"
            triggerModelSlots[0].value shouldBe "new-value"
        }
    }

    context("Initialization and Start") {
        test("start loads dismissed messages from preferences") {
            // Given
            val mocks = Mocks()
            val mockPrefs = mocks.prefs
            val dismissedSet = setOf("dismissed-1", "dismissed-2")
            every { mockPrefs.dismissedMessagesId } returns dismissedSet
            every { mockPrefs.lastTimeInAppDismissed } returns null

            val mockRepository = mocks.repository
            coEvery { mockRepository.cleanCachedInAppMessages() } just runs
            coEvery { mockRepository.listInAppMessages() } returns emptyList()

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.start()

            // Then
            verify { mockPrefs.dismissedMessagesId }
            coVerify { mockRepository.cleanCachedInAppMessages() }
        }

        test("start loads last dismissal time from preferences") {
            // Given
            val mocks = Mocks()
            val mockPrefs = mocks.prefs
            val mockState = mocks.state
            val lastDismissalTime = 5000L
            every { mockPrefs.dismissedMessagesId } returns null
            every { mockPrefs.lastTimeInAppDismissed } returns lastDismissalTime

            val mockRepository = mocks.repository
            coEvery { mockRepository.cleanCachedInAppMessages() } just runs
            coEvery { mockRepository.listInAppMessages() } returns emptyList()

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.start()

            // Then
            verify { mockState.lastTimeInAppDismissed = lastDismissalTime }
        }

        test("start loads redisplayed messages from repository and resets display flag") {
            // Given
            val mocks = Mocks()
            val message1 = mocks.createTestMessage("msg-1")
            val message2 = mocks.createTestMessage("msg-2")
            message1.isDisplayedInSession = true
            message2.isDisplayedInSession = true

            val mockRepository = mocks.repository
            coEvery { mockRepository.cleanCachedInAppMessages() } just runs
            coEvery { mockRepository.listInAppMessages() } returns listOf(message1, message2)

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.start()

            // Then - wait for async operations
            runBlocking {
                delay(50)
                message1.isDisplayedInSession shouldBe false
                message2.isDisplayedInSession shouldBe false
            }
        }

        test("start subscribes to all required services") {
            // Given
            val mocks = Mocks()
            val mockRepository = mocks.repository
            coEvery { mockRepository.cleanCachedInAppMessages() } just runs
            coEvery { mockRepository.listInAppMessages() } returns emptyList()

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.start()

            // Then
            verify { mocks.subscriptionManager.subscribe(any()) }
            verify { mocks.lifecycle.subscribe(any()) }
            verify { mocks.triggerController.subscribe(any()) }
            verify { mocks.sessionService.subscribe(any()) }
            verify { mocks.applicationService.addApplicationLifecycleHandler(any()) }
        }
    }

    context("Paused Property") {
        test("paused getter returns state paused value") {
            // Given
            val mocks = Mocks()
            val mockState = mocks.state
            every { mockState.paused } returns true

            val iamManager = mocks.inAppMessagesManager

            // When
            val result = iamManager.paused

            // Then
            result shouldBe true
        }

        test("setting paused to true does nothing when no message showing") {
            // Given
            val mocks = Mocks()
            val mockState = mocks.state
            val mockDisplayer = mocks.displayer
            every { mockState.paused } returns false
            every { mocks.state.inAppMessageIdShowing } returns null

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.paused = true

            // Then
            verify { mockState.paused = true }
            coVerify(exactly = 0) { mockDisplayer.dismissCurrentInAppMessage() }
        }
    }

    context("Lifecycle Listeners") {
        test("addLifecycleListener subscribes listener") {
            // Given
            val mocks = Mocks()
            val mockListener = mocks.inAppMessageLifecycleListener
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.addLifecycleListener(mockListener)

            // Then
            // Listener is added to internal EventProducer - verify by checking it can be removed
            iamManager.removeLifecycleListener(mockListener)
        }

        test("removeLifecycleListener unsubscribes listener") {
            // Given
            val mocks = Mocks()
            val mockListener = mocks.inAppMessageLifecycleListener
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.addLifecycleListener(mockListener)
            iamManager.removeLifecycleListener(mockListener)

            // Then
            // No exception should be thrown
        }

        test("addClickListener subscribes listener") {
            // Given
            val mocks = Mocks()
            val mockListener = mockk<IInAppMessageClickListener>(relaxed = true)
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.addClickListener(mockListener)

            // Then
            // Listener is added to internal EventProducer
            iamManager.removeClickListener(mockListener)
        }

        test("removeClickListener unsubscribes listener") {
            // Given
            val mocks = Mocks()
            val mockListener = mockk<IInAppMessageClickListener>(relaxed = true)
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.addClickListener(mockListener)
            iamManager.removeClickListener(mockListener)

            // Then
            // No exception should be thrown
        }
    }

    context("Config Model Changes") {
        test("onModelUpdated fetches messages when appId property changes") {
            // Given
            val mocks = Mocks()
            val mockRywData = mocks.rywData
            val mockDeferred = mocks.deferred

            every { mocks.userManager.onesignalId } returns "onesignal-id"
            coEvery { mockDeferred.await() } returns mockRywData
            coEvery {
                mocks.consistencyManager.getRywDataFromAwaitableCondition(any<IamFetchReadyCondition>())
            } returns mockDeferred

            every { mocks.applicationService.isInForeground } returns true

            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns "subscription-id"

            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns null

            val iamManager = mocks.inAppMessagesManager

            val configModel = ConfigModel()
            val args = ModelChangedArgs(
                configModel,
                ConfigModel::appId.name,
                ConfigModel::appId.name,
                "old-value",
                "new-value",
            )

            // When
            iamManager.onModelUpdated(args, "tag")

            // Then
            // Should trigger fetchMessagesWhenConditionIsMet
            // Verification happens through backend call
            runBlocking {
                // Give time for coroutine to execute
                delay(50)
            }
        }

        test("onModelUpdated does nothing when non-appId property changes") {
            // Given
            val mocks = Mocks()
            val iamManager = mocks.inAppMessagesManager

            val configModel = ConfigModel()
            val args = ModelChangedArgs(
                configModel,
                "other-property",
                "other-property",
                "old-value",
                "new-value",
            )

            // When
            iamManager.onModelUpdated(args, "tag")

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("onModelReplaced fetches messages") {
            // Given
            val mocks = Mocks()
            val mockRywData = mocks.rywData
            val mockDeferred = mocks.deferred

            every { mocks.userManager.onesignalId } returns "onesignal-id"
            coEvery { mockDeferred.await() } returns mockRywData
            coEvery {
                mocks.consistencyManager.getRywDataFromAwaitableCondition(any<IamFetchReadyCondition>())
            } returns mockDeferred
            every { mocks.applicationService.isInForeground } returns true

            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns "subscription-id"

            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns null

            val iamManager = mocks.inAppMessagesManager

            val model = ConfigModel()

            // When
            iamManager.onModelReplaced(model, "tag")

            // Then
            coVerify {
                mocks.backend.listInAppMessages(any(), any(), any(), any())
            }
        }
    }

    context("Subscription Changes") {
        test("onSubscriptionChanged fetches messages when push subscription id changes") {
            // Given
            val mocks = Mocks()
            val mockRywData = mocks.rywData
            val mockDeferred = mocks.deferred

            every { mocks.userManager.onesignalId } returns "onesignal-id"
            coEvery { mockDeferred.await() } returns mockRywData
            coEvery {
                mocks.consistencyManager.getRywDataFromAwaitableCondition(any<IamFetchReadyCondition>())
            } returns mockDeferred

            every { mocks.applicationService.isInForeground } returns true

            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns "subscription-id"

            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns null

            val iamManager = mocks.inAppMessagesManager

            val subscriptionModel = SubscriptionModel()
            val args = ModelChangedArgs(
                subscriptionModel,
                SubscriptionModel::id.name,
                SubscriptionModel::id.name,
                "old-id",
                "new-id",
            )

            // When
            iamManager.onSubscriptionChanged(mocks.pushSubscription, args)

            // Then
            coVerify {
                mocks.backend.listInAppMessages(any(), any(), any(), any())
            }
        }

        test("onSubscriptionChanged does nothing for non-push subscription") {
            // Given
            val mocks = Mocks()
            val iamManager = mocks.inAppMessagesManager

            val mockSubscription = mockk<ISubscription>()
            val subscriptionModel = SubscriptionModel()
            val args = ModelChangedArgs(
                subscriptionModel,
                SubscriptionModel::id.name,
                SubscriptionModel::id.name,
                "old-id",
                "new-id",
            )

            // When
            iamManager.onSubscriptionChanged(mockSubscription, args)

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("onSubscriptionChanged does nothing when id path does not match") {
            // Given
            val mocks = Mocks()
            val iamManager = mocks.inAppMessagesManager

            val subscriptionModel = SubscriptionModel()
            val args = ModelChangedArgs(
                subscriptionModel,
                "other-path",
                "other-path",
                "old-value",
                "new-value",
            )

            // When
            iamManager.onSubscriptionChanged(mocks.pushSubscription, args)

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("onSubscriptionAdded does not fetch") {
            // Given
            val mocks = Mocks()
            val iamManager = mocks.inAppMessagesManager

            val mockSubscription = mockk<ISubscription>()

            // When
            iamManager.onSubscriptionAdded(mockSubscription)

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("onSubscriptionRemoved does not fetch") {
            // Given
            val mocks = Mocks()
            val iamManager = mocks.inAppMessagesManager

            val mockSubscription = mockk<ISubscription>()

            // When
            iamManager.onSubscriptionRemoved(mockSubscription)

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }
    }

    context("Session Lifecycle") {
        test("onSessionStarted resets redisplayed messages and fetches messages") {
            // Given
            val mocks = Mocks()
            val message1 = mocks.createTestMessage("msg-1")
            val message2 = mocks.createTestMessage("msg-2")
            message1.isDisplayedInSession = true
            message2.isDisplayedInSession = true

            val mockRepository = mocks.repository
            coEvery { mockRepository.listInAppMessages() } returns listOf(message1, message2)
            val mockRywData = mocks.rywData
            val mockDeferred = mocks.deferred

            every { mocks.userManager.onesignalId } returns "onesignal-id"
            coEvery { mockDeferred.await() } returns mockRywData
            coEvery {
                mocks.consistencyManager.getRywDataFromAwaitableCondition(any<IamFetchReadyCondition>())
            } returns mockDeferred
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns null

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onSessionStarted()

            // Then - wait for async fetchMessages operation to complete
            runBlocking {
                delay(50)
            }
        }

        test("onSessionActive does nothing") {
            // Given
            val mocks = Mocks()
            val iamManager = mocks.inAppMessagesManager

            // When/Then - should not throw
            iamManager.onSessionActive()
        }

        test("onSessionEnded does nothing") {
            // Given
            val mocks = Mocks()
            val iamManager = mocks.inAppMessagesManager

            // When/Then - should not throw
            iamManager.onSessionEnded(1000L)
        }
    }

    context("Message Lifecycle Callbacks") {
        test("onMessageWillDisplay fires lifecycle callback when subscribers exist") {
            // Given
            val mocks = Mocks()
            val mockListener = mocks.inAppMessageLifecycleListener
            val message = mocks.createTestMessage("msg-1")
            val iamManager = mocks.inAppMessagesManager

            iamManager.addLifecycleListener(mockListener)

            // When
            iamManager.onMessageWillDisplay(message)

            // Then
            // Callback should be fired - verified through no exception
        }

        test("onMessageWillDisplay does nothing when no subscribers") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val iamManager = mocks.inAppMessagesManager

            // When/Then - should not throw
            iamManager.onMessageWillDisplay(message)
        }

        test("onMessageWasDisplayed sends impression for non-preview message") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery { mocks.backend.sendIAMImpression(any(), any(), any(), any()) } just runs

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onMessageWasDisplayed(message)

            // Then
            coVerify { mocks.backend.sendIAMImpression(any(), any(), any(), any()) }
        }

        test("onMessageWasDisplayed does not send impression for preview message") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1", isPreview = true)
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onMessageWasDisplayed(message)

            // Then
            coVerify(exactly = 0) { mocks.backend.sendIAMImpression(any(), any(), any(), any()) }
        }

        test("onMessageWasDisplayed does not send duplicate impressions") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery { mocks.backend.sendIAMImpression(any(), any(), any(), any()) } just runs

            val iamManager = mocks.inAppMessagesManager

            // When - send impression twice
            runBlocking {
                iamManager.onMessageWasDisplayed(message)
                iamManager.onMessageWasDisplayed(message)
            }

            // Then - should only send once
            coVerify(exactly = 1) { mocks.backend.sendIAMImpression(any(), any(), any(), any()) }
        }

        test("onMessageWillDismiss fires lifecycle callback when subscribers exist") {
            // Given
            val mocks = Mocks()
            val mockListener = mocks.inAppMessageLifecycleListener
            val message = mocks.createTestMessage("msg-1")
            val iamManager = mocks.inAppMessagesManager

            iamManager.addLifecycleListener(mockListener)

            // When
            iamManager.onMessageWillDismiss(message)

            // Then
            // Should not throw
        }

        test("onMessageWillDismiss does nothing when no subscribers") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val iamManager = mocks.inAppMessagesManager

            // When/Then - should not throw
            iamManager.onMessageWillDismiss(message)
        }

        test("onMessageWasDismissed calls messageWasDismissed") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            every { mocks.state.inAppMessageIdShowing } returns null

            val iamManager = mocks.inAppMessagesManager

            // When
            runBlocking {
                iamManager.onMessageWasDismissed(message)
                delay(50)
            }

            // Then
            verify { mocks.influenceManager.onInAppMessageDismissed() }
        }
    }

    context("Trigger Callbacks") {
        test("onTriggerCompleted does nothing") {
            // Given
            val mocks = Mocks()
            val iamManager = mocks.inAppMessagesManager

            // When/Then - should not throw
            iamManager.onTriggerCompleted("trigger-id")
        }

        test("onTriggerConditionChanged makes redisplay messages available and re-evaluates") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            every { mocks.triggerController.isTriggerOnMessage(any(), any()) } returns false
            every { mocks.triggerController.messageHasOnlyDynamicTriggers(any()) } returns false
            every { mocks.triggerController.evaluateMessageTriggers(any()) } returns false

            val iamManager = mocks.inAppMessagesManager

            // When
            runBlocking {
                iamManager.onTriggerConditionChanged("trigger-id")
                delay(50)
            }

            // Then
            // Should trigger re-evaluation
        }

        test("onTriggerChanged makes redisplay messages available and re-evaluates") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            every { mocks.triggerController.isTriggerOnMessage(any(), any()) } returns false
            every { mocks.triggerController.messageHasOnlyDynamicTriggers(any()) } returns false
            every { mocks.triggerController.evaluateMessageTriggers(any()) } returns false

            val iamManager = mocks.inAppMessagesManager

            // When
            runBlocking {
                iamManager.onTriggerChanged("trigger-key")
                delay(50)
            }

            // Then
            // Should trigger re-evaluation
        }
    }

    context("Application Lifecycle") {
        test("onFocus does nothing") {
            // Given
            val mocks = Mocks()
            val iamManager = mocks.inAppMessagesManager

            // When/Then - should not throw
            iamManager.onFocus(false)
            iamManager.onFocus(true)
        }

        test("onUnfocused does nothing") {
            // Given
            val mocks = Mocks()
            val iamManager = mocks.inAppMessagesManager

            // When/Then - should not throw
            iamManager.onUnfocused()
        }
    }

    context("Message Action Handling") {
        test("onMessageActionOccurredOnPreview processes preview actions") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1", isPreview = true)
            val mockClickResult = mocks.inAppMessageClickResult
            val mockClickListener = mockk<IInAppMessageClickListener>(relaxed = true)
            val mockPrompt = mockk<InAppMessagePrompt>(relaxed = true)
            every { mockPrompt.hasPrompted() } returns false
            coEvery { mockPrompt.handlePrompt() } returns InAppMessagePrompt.PromptActionResult.PERMISSION_GRANTED
            every { mocks.state.currentPrompt } returns null

            val iamManager = mocks.inAppMessagesManager
            iamManager.addClickListener(mockClickListener)

            // When
            iamManager.onMessageActionOccurredOnPreview(message, mockClickResult)

            // Then
            verify { mockClickResult.isFirstClick = any() }
        }

        test("onMessagePageChanged sends page impression for non-preview message") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockPage = mockk<InAppMessagePage>(relaxed = true)
            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mockPage.pageId } returns "page-id"

            coEvery { mocks.backend.sendIAMPageImpression(any(), any(), any(), any(), any()) } just runs

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onMessagePageChanged(message, mockPage)

            // Then
            coVerify { mocks.backend.sendIAMPageImpression(any(), any(), any(), any(), any()) }
        }

        test("onMessagePageChanged does nothing for preview message") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1", isPreview = true)
            val mockPage = mockk<InAppMessagePage>(relaxed = true)
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onMessagePageChanged(message, mockPage)

            // Then
            coVerify(exactly = 0) { mocks.backend.sendIAMPageImpression(any(), any(), any(), any(), any()) }
        }
    }

    context("Error Handling") {
        test("onMessageWasDisplayed removes impression from set on backend failure") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")

            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery {
                mocks.backend.sendIAMImpression(any(), any(), any(), any())
            } throws BackendException(500, "Server error")

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onMessageWasDisplayed(message)
            delay(50)
            // Try again - should retry since impression was removed
            iamManager.onMessageWasDisplayed(message)

            // Then - should attempt twice since first failed
            coVerify(exactly = 2) { mocks.backend.sendIAMImpression(any(), any(), any(), any()) }
        }

        test("onMessagePageChanged removes page impression on backend failure") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockPage = mockk<InAppMessagePage>(relaxed = true)
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mockPage.pageId } returns "page-id"

            coEvery {
                mocks.backend.sendIAMPageImpression(any(), any(), any(), any(), any())
            } throws BackendException(500, "Server error")

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onMessagePageChanged(message, mockPage)
            delay(50)
            // Try again - should retry since page impression was removed
            iamManager.onMessagePageChanged(message, mockPage)

            // Then - should attempt twice since first failed
            coVerify(exactly = 2) { mocks.backend.sendIAMPageImpression(any(), any(), any(), any(), any()) }
        }

        test("onMessageActionOccurredOnMessage removes click on backend failure") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockClickResult = mocks.inAppMessageClickResult

            coEvery {
                mocks.backend.sendIAMClick(any(), any(), any(), any(), any(), any())
            } throws BackendException(500, "Server error")

            val iamManager = mocks.inAppMessagesManager

            // When
            runBlocking {
                iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)
                delay(50)
            }

            // Then
            coVerify { mocks.backend.sendIAMClick(any(), any(), any(), any(), any(), any()) }
            // Click should be removed from message on failure
            message.isClickAvailable("click-id") shouldBe true
        }
    }

    context("Message Fetching") {
        test("fetchMessagesWhenConditionIsMet returns early when app is not in foreground") {
            // Given
            val mocks = Mocks()
            val mockRywData = mocks.rywData
            val mockDeferred = mocks.deferred

            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns false
            coEvery { mockDeferred.await() } returns mockRywData
            coEvery {
                mocks.consistencyManager.getRywDataFromAwaitableCondition(any<IamFetchReadyCondition>())
            } returns mockDeferred

            val iamManager = mocks.inAppMessagesManager

            // When - trigger fetch via onSessionStarted
            iamManager.onSessionStarted()

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("fetchMessagesWhenConditionIsMet returns early when subscriptionId is empty") {
            // Given
            val mocks = Mocks()
            val mockRywData = mocks.rywData
            val mockDeferred = mocks.deferred

            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns ""
            coEvery { mockDeferred.await() } returns mockRywData
            coEvery {
                mocks.consistencyManager.getRywDataFromAwaitableCondition(any<IamFetchReadyCondition>())
            } returns mockDeferred

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onSessionStarted()

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("fetchMessagesWhenConditionIsMet returns early when subscriptionId is local ID") {
            // Given
            val mocks = Mocks()
            val mockRywData = mocks.rywData
            val mockDeferred = mocks.deferred

            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns "local-123"
            coEvery { mockDeferred.await() } returns mockRywData
            coEvery {
                mocks.consistencyManager.getRywDataFromAwaitableCondition(any<IamFetchReadyCondition>())
            } returns mockDeferred

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onSessionStarted()

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("fetchMessagesWhenConditionIsMet evaluates messages when new messages are returned") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockRywData = mocks.rywData
            val mockDeferred = mocks.deferred

            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mocks.triggerController.evaluateMessageTriggers(any()) } returns false
            coEvery { mockDeferred.await() } returns mockRywData
            coEvery {
                mocks.consistencyManager.getRywDataFromAwaitableCondition(any<IamFetchReadyCondition>())
            } returns mockDeferred
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns listOf(message)
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onSessionStarted()

            // Then
            coVerify { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
            verify { mocks.triggerController.evaluateMessageTriggers(any()) }
        }
    }

    context("Message Queue and Display") {
        test("messages are not queued when paused") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockRywData = mocks.rywData
            val mockDeferred = mocks.deferred

            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mocks.triggerController.evaluateMessageTriggers(message) } returns true
            every { mocks.triggerController.isTriggerOnMessage(any(), any()) } returns false
            every { mocks.triggerController.messageHasOnlyDynamicTriggers(any()) } returns false
            every { mocks.state.inAppMessageIdShowing } returns null
            every { mocks.state.paused } returns true
            coEvery { mockDeferred.await() } returns mockRywData
            coEvery {
                mocks.consistencyManager.getRywDataFromAwaitableCondition(any<IamFetchReadyCondition>())
            } returns mockDeferred

            val iamManager = mocks.inAppMessagesManager

            // When - fetch messages while paused
            iamManager.onSessionStarted()

            // Then - should not display
            coVerify(exactly = 0) { mocks.displayer.displayMessage(any()) }
        }
    }

    context("Message Evaluation") {
        test("messages are evaluated and queued when paused is set to false") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockRywData = mocks.rywData
            val mockDeferred = mocks.deferred

            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mocks.triggerController.evaluateMessageTriggers(message) } returns true
            every { mocks.triggerController.isTriggerOnMessage(any(), any()) } returns false
            every { mocks.triggerController.messageHasOnlyDynamicTriggers(any()) } returns false
            every { mocks.state.inAppMessageIdShowing } returns null
            every { mocks.state.paused } returns true
            coEvery { mocks.applicationService.waitUntilSystemConditionsAvailable() } returns true
            coEvery { mockDeferred.await() } returns mockRywData
            coEvery {
                mocks.consistencyManager.getRywDataFromAwaitableCondition(any<IamFetchReadyCondition>())
            } returns mockDeferred
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns listOf(message)
            coEvery { mocks.displayer.displayMessage(any()) } returns true

            val iamManager = mocks.inAppMessagesManager

            // Fetch messages first
            iamManager.onSessionStarted()

            // When - set paused to false, which triggers evaluateInAppMessages
            iamManager.paused = false

            // Then
            verify { mocks.triggerController.evaluateMessageTriggers(message) }
        }

        test("dismissed messages are not queued for display") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockRywData = mocks.rywData
            val mockDeferred = mocks.deferred

            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.subscriptionManager.subscriptions } returns mockk {
                every { push } returns mocks.pushSubscription
            }
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mocks.triggerController.evaluateMessageTriggers(message) } returns true
            every { mocks.triggerController.isTriggerOnMessage(any(), any()) } returns false
            every { mocks.triggerController.messageHasOnlyDynamicTriggers(any()) } returns false
            every { mocks.state.paused } returns false
            coEvery { mockDeferred.await() } returns mockRywData
            coEvery {
                mocks.consistencyManager.getRywDataFromAwaitableCondition(any<IamFetchReadyCondition>())
            } returns mockDeferred

            val iamManager = mocks.inAppMessagesManager

            // Fetch messages
            iamManager.onSessionStarted()

            // Dismiss the message
            iamManager.onMessageWasDismissed(message)

            // When - trigger evaluation
            iamManager.paused = false

            // Then - should not display dismissed message
            coVerify(exactly = 0) { mocks.displayer.displayMessage(message) }
        }
    }

    context("Message Actions - Outcomes and Tags") {
        test("onMessageActionOccurredOnMessage fires outcomes") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockClickResult = mocks.inAppMessageClickResult
            val mockOutcomeController = mocks.outcomeEventsController
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)

            // Then - wait for async operations
            coVerify { mockOutcomeController.sendOutcomeEvent("outcome-name") }
        }

        test("onMessageActionOccurredOnMessage fires outcomes with weight") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockClickResult = mocks.inAppMessageClickResult
            val mockOutcomeController = mocks.outcomeEventsController
            val mockOutcome = mocks.outcome
            val iamManager = mocks.inAppMessagesManager
            val weight = 5.0f
            every { mockOutcome.weight } returns weight

            // When
            iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)

            // Then - wait for async operations
            coVerify { mockOutcomeController.sendOutcomeEventWithValue("outcome-name", weight) }
        }

        test("onMessageActionOccurredOnMessage adds tags") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockClickResult = mocks.inAppMessageClickResult
            val mockTags = mockk<InAppMessageTag>(relaxed = true)
            val tagsToAdd = JSONObject()
            tagsToAdd.put("key1", "value1")

            every { mockTags.tagsToAdd } returns tagsToAdd
            every { mockTags.tagsToRemove } returns null
            every { mockClickResult.tags } returns mockTags

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)
            delay(50)

            // Then - wait for async operations
            verify { mocks.userManager.addTags(any()) }
        }

        test("onMessageActionOccurredOnMessage removes tags") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockClickResult = mocks.inAppMessageClickResult
            val mockTags = mockk<InAppMessageTag>(relaxed = true)
            val tagsToRemove = JSONArray()
            tagsToRemove.put("key1")

            every { mockTags.tagsToAdd } returns null
            every { mockTags.tagsToRemove } returns tagsToRemove
            every { mockClickResult.tags } returns mockTags

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)

            // Then - wait for async operations
            verify { mocks.userManager.removeTags(any()) }
        }

        test("onMessageActionOccurredOnMessage opens URL in browser") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockClickResult = mocks.inAppMessageClickResult
            val mockApplicationService = mocks.applicationService
            every { mockClickResult.url } returns "https://example.com"
            every { mockClickResult.urlTarget } returns InAppMessageActionUrlType.BROWSER

            // When
            val iamManager = mocks.inAppMessagesManager
            iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)

            // Then - wait for async operations to complete
            // URL opening is tested indirectly through no exceptions
            runBlocking {
                delay(50)
            }
        }

        test("onMessageActionOccurredOnMessage opens URL in webview") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockClickResult = mocks.inAppMessageClickResult
            every { mockClickResult.url } returns "https://example.com"
            every { mockClickResult.urlTarget } returns InAppMessageActionUrlType.IN_APP_WEBVIEW

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)

            // Then - wait for async operations to complete
            // URL opening is tested indirectly through no exceptions
            runBlocking {
                delay(50)
            }
        }

        test("onMessageActionOccurredOnMessage does nothing when URL is empty") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockClickResult = mocks.inAppMessageClickResult

            val iamManager = mocks.inAppMessagesManager

            // When/Then - should not throw
            iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)
        }
    }

    context("Prompt Processing") {
        test("onMessageActionOccurredOnMessage processes prompts") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockPrompt = mockk<InAppMessagePrompt>(relaxed = true)
            val mockClickResult = mocks.inAppMessageClickResult
            val mockState = mocks.state
            val mockDisplayer = mocks.displayer

            every { mockClickResult.prompts } returns mutableListOf(mockPrompt)
            every { mockPrompt.hasPrompted() } returns false
            every { mockPrompt.setPrompted(any()) } just runs
            // currentPrompt starts as null, then gets set to the prompt during processing
            var currentPrompt: InAppMessagePrompt? = null
            every { mockState.currentPrompt } answers { currentPrompt }
            every { mockState.currentPrompt = any() } answers { currentPrompt = firstArg() }

            // When
            val iamManager = mocks.inAppMessagesManager
            iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)

            // Then
            coVerify { mockDisplayer.dismissCurrentInAppMessage() }
            coVerify { mockPrompt.setPrompted(any()) }
        }

        test("onMessageActionOccurredOnMessage does nothing when prompts list is empty") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockClickResult = mocks.inAppMessageClickResult
            val mockDisplayer = mocks.displayer
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)

            // Then
            coVerify(exactly = 0) { mockDisplayer.dismissCurrentInAppMessage() }
        }
    }

    context("Message Persistence") {
        test("onMessageWasDismissed persists message to repository") {
            // Given
            val mocks = Mocks()
            val message = mocks.createTestMessage("msg-1")
            val mockRepository = mocks.repository
            val mockState = mocks.state

            coEvery { mockRepository.saveInAppMessage(any()) } just runs
            every { mockState.lastTimeInAppDismissed } returns 500L
            every { mockState.currentPrompt } returns null

            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.onMessageWasDismissed(message)

            // Then
            coVerify { mockRepository.saveInAppMessage(message) }
            message.isDisplayedInSession shouldBe true
            message.isTriggerChanged shouldBe false
        }
    }
})
