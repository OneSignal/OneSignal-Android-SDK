package com.onesignal.inAppMessages.internal

import android.content.Context
import com.onesignal.common.AndroidUtils
import com.onesignal.common.consistency.IamFetchReadyCondition
import com.onesignal.common.consistency.RywData
import com.onesignal.common.consistency.models.IConsistencyManager
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.IInAppMessageClickListener
import com.onesignal.inAppMessages.IInAppMessageLifecycleListener
import com.onesignal.inAppMessages.InAppMessageActionUrlType
import com.onesignal.inAppMessages.internal.backend.IInAppBackendService
import com.onesignal.inAppMessages.internal.common.OneSignalChromeTab
import com.onesignal.inAppMessages.internal.display.IInAppDisplayer
import com.onesignal.inAppMessages.internal.lifecycle.IInAppLifecycleService
import com.onesignal.inAppMessages.internal.preferences.IInAppPreferencesController
import com.onesignal.inAppMessages.internal.prompt.impl.InAppMessagePrompt
import com.onesignal.inAppMessages.internal.repositories.IInAppRepository
import com.onesignal.inAppMessages.internal.state.InAppStateService
import com.onesignal.inAppMessages.internal.triggers.ITriggerController
import com.onesignal.inAppMessages.internal.triggers.TriggerModel
import com.onesignal.inAppMessages.internal.triggers.TriggerModelStore
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.IOMockHelper.awaitIO
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
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
    val inAppStateService = mockk<InAppStateService>(relaxed = true)
    val inAppPreferencesController = mockk<IInAppPreferencesController>(relaxed = true)
    val repository = mockk<IInAppRepository>(relaxed = true)
    val backend = mockk<IInAppBackendService>(relaxed = true)
    val triggerController = mockk<ITriggerController>(relaxed = true)
    val triggerModelStore = mockk<TriggerModelStore>(relaxed = true)
    val inAppDisplayer = mockk<IInAppDisplayer>(relaxed = true)
    val inAppLifecycleService = mockk<IInAppLifecycleService>(relaxed = true)
    val languageContext = MockHelper.languageContext()
    val time = MockHelper.time(1000)
    val inAppMessageLifecycleListener = spyk<IInAppMessageLifecycleListener>()
    val inAppMessageClickListener = spyk<IInAppMessageClickListener>()
    val rywData = RywData("token", 100L)

    val rywDeferred = mockk<CompletableDeferred<RywData?>> {
        coEvery { await() } returns rywData
    }

    val consistencyManager = mockk<IConsistencyManager>(relaxed = true) {
        coEvery { getRywDataFromAwaitableCondition(any<IamFetchReadyCondition>()) } returns rywDeferred
    }

    val subscriptionManager = mockk<ISubscriptionManager>(relaxed = true) {
        every { subscriptions } returns mockk {
            every { push } returns pushSubscription
        }
    }

    val testOutcome =
        run {
            val outcome = mockk<InAppMessageOutcome>(relaxed = true)
            every { outcome.name } returns "outcome-name"
            outcome
        }

    val inAppMessageClickResult =
        run {
            val result = mockk<InAppMessageClickResult>(relaxed = true)
            every { result.prompts } returns mutableListOf()
            every { result.outcomes } returns mutableListOf(testOutcome)
            every { result.tags } returns null
            every { result.url } returns null
            every { result.clickId } returns "click-id"
            result
        }

    // factory-style so every access returns a new message:
    val testInAppMessage: InAppMessage
        get() {
            val json = JSONObject()
            json.put("id", "test-message-id")
            val variantsJson = JSONObject()
            val allVariantJson = JSONObject()
            allVariantJson.put("en", "variant-id-123")
            variantsJson.put("all", allVariantJson)
            json.put("variants", variantsJson)
            json.put("triggers", JSONArray())
            return InAppMessage(json, time)
        }

    // factory-style so every access returns a new message:
    val testInAppMessagePreview: InAppMessage
        get() = InAppMessage(true, time)

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
        inAppStateService,
        inAppPreferencesController,
        repository,
        backend,
        triggerController,
        triggerModelStore,
        inAppDisplayer,
        inAppLifecycleService,
        languageContext,
        time,
        consistencyManager,
    )
}

class InAppMessagesManagerTests : FunSpec({

    lateinit var mocks: Mocks

    // register to access awaitIO()
    listener(IOMockHelper)

    beforeAny {
        Logging.logLevel = LogLevel.NONE
        mocks = Mocks() // fresh instance for each test
    }

    beforeSpec {
        // required when testing functions that internally call suspendifyOnMain
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    afterSpec {
        Dispatchers.resetMain()
    }

    context("Trigger Management") {
        test("triggers are backed by the trigger model store") {
            // Given
            val mockTriggerModelStore = mocks.triggerModelStore
            val triggerModelSlots = mutableListOf<TriggerModel>()
            val iamManager = mocks.inAppMessagesManager
            every { mockTriggerModelStore.get(any()) } returns null
            every { mockTriggerModelStore.add(capture(triggerModelSlots)) } answers {}
            every { mockTriggerModelStore.remove(any()) } just runs
            every { mockTriggerModelStore.clear() } just runs

            // When
            iamManager.addTrigger("trigger-key1", "trigger-value1")
            iamManager.addTriggers(mapOf("trigger-key2" to "trigger-value2", "trigger-key3" to "trigger-value3"))
            iamManager.removeTrigger("trigger-key4")
            iamManager.removeTriggers(listOf("trigger-key5", "trigger-key6"))
            iamManager.clearTriggers()

            // Then
            with(triggerModelSlots[0]) { key to value } shouldBe ("trigger-key1" to "trigger-value1")
            with(triggerModelSlots[1]) { key to value } shouldBe ("trigger-key2" to "trigger-value2")
            with(triggerModelSlots[2]) { key to value } shouldBe ("trigger-key3" to "trigger-value3")
            verify(exactly = 1) { mockTriggerModelStore.remove("trigger-key4") }
            verify(exactly = 1) { mockTriggerModelStore.remove("trigger-key5") }
            verify(exactly = 1) { mockTriggerModelStore.remove("trigger-key6") }
            verify(exactly = 1) { mockTriggerModelStore.clear() }
        }

        test("addTrigger updates existing trigger model when trigger already exists") {
            // Given
            val mockTriggerModelStore = mocks.triggerModelStore
            val existingTrigger = TriggerModel().apply {
                id = "existing-key"
                key = "existing-key"
                value = "old-value"
            }
            every { mockTriggerModelStore.get("existing-key") } returns existingTrigger
            every { mockTriggerModelStore.add(any()) } just runs

            // When
            mocks.inAppMessagesManager.addTrigger("existing-key", "new-value")

            // Then
            existingTrigger.value shouldBe "new-value"
            verify(exactly = 0) { mockTriggerModelStore.add(any()) }
        }

        test("addTrigger creates new trigger model when trigger does not exist") {
            // Given
            val mockTriggerModelStore = mocks.triggerModelStore
            val triggerModelSlots = mutableListOf<TriggerModel>()
            every { mockTriggerModelStore.get("new-key") } returns null
            every { mockTriggerModelStore.add(capture(triggerModelSlots)) } answers {}

            // When
            mocks.inAppMessagesManager.addTrigger("new-key", "new-value")

            // Then
            triggerModelSlots.size shouldBe 1
            with(triggerModelSlots[0]) { key to value } shouldBe ("new-key" to "new-value")
        }
    }

    context("Initialization and Start") {
        test("start loads dismissed messages from preferences") {
            // Given
            val mockPrefs = mocks.inAppPreferencesController
            val dismissedSet = setOf("dismissed-1", "dismissed-2")
            val mockRepository = mocks.repository
            every { mockPrefs.dismissedMessagesId } returns dismissedSet
            every { mockPrefs.lastTimeInAppDismissed } returns null
            coEvery { mockRepository.cleanCachedInAppMessages() } just runs
            coEvery { mockRepository.listInAppMessages() } returns emptyList()

            // When
            mocks.inAppMessagesManager.start()

            // Then
            verify { mockPrefs.dismissedMessagesId }
            coVerify { mockRepository.cleanCachedInAppMessages() }
        }

        test("start loads last dismissal time from preferences") {
            // Given
            val mockPrefs = mocks.inAppPreferencesController
            val mockState = mocks.inAppStateService
            val lastDismissalTime = 5000L
            every { mockPrefs.dismissedMessagesId } returns null
            every { mockPrefs.lastTimeInAppDismissed } returns lastDismissalTime
            val mockRepository = mocks.repository
            coEvery { mockRepository.cleanCachedInAppMessages() } just runs
            coEvery { mockRepository.listInAppMessages() } returns emptyList()

            // When
            mocks.inAppMessagesManager.start()

            // Then
            verify { mockState.lastTimeInAppDismissed = lastDismissalTime }
        }

        test("start loads redisplayed messages from repository and resets display flag") {
            // Given
            val message1 = mocks.testInAppMessage
            val message2 = mocks.testInAppMessage
            message1.isDisplayedInSession = true
            message2.isDisplayedInSession = true
            val mockRepository = mocks.repository
            coEvery { mockRepository.cleanCachedInAppMessages() } just runs
            coEvery { mockRepository.listInAppMessages() } returns listOf(message1, message2)

            // When
            mocks.inAppMessagesManager.start()
            awaitIO()

            // Then
            message1.isDisplayedInSession shouldBe false
            message2.isDisplayedInSession shouldBe false
        }

        test("start subscribes to all required services") {
            // Given
            val mockRepository = mocks.repository
            coEvery { mockRepository.cleanCachedInAppMessages() } just runs
            coEvery { mockRepository.listInAppMessages() } returns emptyList()

            // When
            mocks.inAppMessagesManager.start()

            // Then
            verify { mocks.subscriptionManager.subscribe(any()) }
            verify { mocks.inAppLifecycleService.subscribe(any()) }
            verify { mocks.triggerController.subscribe(any()) }
            verify { mocks.sessionService.subscribe(any()) }
            verify { mocks.applicationService.addApplicationLifecycleHandler(any()) }
        }
    }

    context("Paused Property") {
        test("paused getter returns state paused value") {
            // Given
            every { mocks.inAppStateService.paused } returns true

            // When
            val result = mocks.inAppMessagesManager.paused

            // Then
            result shouldBe true
        }

        test("setting paused to true does nothing when no message showing") {
            // Given
            val mockState = mocks.inAppStateService
            val mockDisplayer = mocks.inAppDisplayer
            val iamManager = mocks.inAppMessagesManager
            every { mockState.paused } returns false
            every { mocks.inAppStateService.inAppMessageIdShowing } returns null

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
            val mockListener = mocks.inAppMessageLifecycleListener
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.addLifecycleListener(mockListener)
            iamManager.onMessageWillDisplay(mocks.testInAppMessage)

            // Then
            // Verify listener callback was called
            verify { mockListener.onWillDisplay(any()) }
        }

        test("removeLifecycleListener unsubscribes listener") {
            // Given
            val mockListener = mocks.inAppMessageLifecycleListener
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.addLifecycleListener(mockListener)
            iamManager.removeLifecycleListener(mockListener)
            iamManager.onMessageWillDisplay(mocks.testInAppMessage)

            // Then
            // Listener should not be called after removal
            verify(exactly = 0) { mockListener.onWillDisplay(any()) }
        }

        test("addClickListener subscribes listener") {
            // Given
            val mockListener = mocks.inAppMessageClickListener
            val message = mocks.testInAppMessage
            val mockClickResult = mocks.inAppMessageClickResult
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.addClickListener(mockListener)
            iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)

            // Then
            // Verify listener callback was called
            verify { mockListener.onClick(any()) }
        }

        test("removeClickListener unsubscribes listener") {
            // Given
            val mockListener = mockk<IInAppMessageClickListener>(relaxed = true)
            val message = mocks.testInAppMessage
            val mockClickResult = mocks.inAppMessageClickResult
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.addClickListener(mockListener)
            iamManager.removeClickListener(mockListener)
            iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)

            // Then
            // Listener should not be called after removal
            verify(exactly = 0) { mockListener.onClick(any()) }
        }
    }

    context("Config Model Changes") {
        test("onModelUpdated fetches messages when appId property changes") {
            // Given
            val mockDeferred = mocks.rywDeferred
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns null
            val args = ModelChangedArgs(
                ConfigModel(),
                ConfigModel::appId.name,
                ConfigModel::appId.name,
                "old-value",
                "new-value",
            )

            // When
            mocks.inAppMessagesManager.onModelUpdated(args, "tag")
            awaitIO()

            // Then
            // Should trigger fetchMessagesWhenConditionIsMet
            coVerify { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("onModelUpdated does nothing when non-appId property changes") {
            // Given
            val args = ModelChangedArgs(
                ConfigModel(),
                "other-property",
                "other-property",
                "old-value",
                "new-value",
            )

            // When
            mocks.inAppMessagesManager.onModelUpdated(args, "tag")

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("onModelReplaced fetches messages") {
            // Given
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true

            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns null

            // When
            mocks.inAppMessagesManager.onModelReplaced(ConfigModel(), "tag")
            awaitIO()

            // Then
            coVerify {
                mocks.backend.listInAppMessages(any(), any(), any(), any())
            }
        }
    }

    context("Subscription Changes") {
        test("onSubscriptionChanged fetches messages when push subscription id changes") {
            // Given
            val mockDeferred = mocks.rywDeferred
            val subscriptionModel = SubscriptionModel()
            val args = ModelChangedArgs(
                subscriptionModel,
                SubscriptionModel::id.name,
                SubscriptionModel::id.name,
                "old-id",
                "new-id",
            )
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns null

            // When
            mocks.inAppMessagesManager.onSubscriptionChanged(mocks.pushSubscription, args)

            // Then
            coVerify {
                mocks.backend.listInAppMessages(any(), any(), any(), any())
            }
        }

        test("onSubscriptionChanged does nothing for non-push subscription") {
            // Given
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
            val iamManager = mocks.inAppMessagesManager
            val mockSubscription = mockk<ISubscription>()

            // When
            iamManager.onSubscriptionAdded(mockSubscription)

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("onSubscriptionRemoved does not fetch") {
            // Given
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
            val message1 = mocks.testInAppMessage
            val message2 = mocks.testInAppMessage
            val mockRywData = mocks.rywData
            val mockDeferred = mocks.rywDeferred
            val mockRepository = mocks.repository

            message1.isDisplayedInSession = true
            message2.isDisplayedInSession = true
            coEvery { mockRepository.listInAppMessages() } returns listOf(message1, message2)
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            coEvery { mockDeferred.await() } returns mockRywData
            coEvery {
                mocks.consistencyManager.getRywDataFromAwaitableCondition(any<IamFetchReadyCondition>())
            } returns mockDeferred
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns null

            // When
            mocks.inAppMessagesManager.start()
            mocks.inAppMessagesManager.onSessionStarted()
            awaitIO()

            // Then
            // Verify messages were reset and backend was called
            message1.isDisplayedInSession shouldBe false
            message2.isDisplayedInSession shouldBe false
            coVerify { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("onSessionActive does nothing") {
            // Given
            val iamManager = mocks.inAppMessagesManager

            // When/Then - should not throw
            iamManager.onSessionActive()

            // Verified by no exception being thrown
        }

        test("onSessionEnded does nothing") {
            // Given
            val iamManager = mocks.inAppMessagesManager

            // When/Then - should not throw
            iamManager.onSessionEnded(10L)

            // Verified by no exception being thrown
        }
    }

    context("Message Lifecycle Callbacks") {
        test("onMessageWillDisplay fires lifecycle callback when subscribers exist") {
            // Given
            mocks.inAppMessagesManager.addLifecycleListener(mocks.inAppMessageLifecycleListener)

            // When
            mocks.inAppMessagesManager.onMessageWillDisplay(mocks.testInAppMessage)

            // Then
            // Verify callback was fired
            verify { mocks.inAppMessageLifecycleListener.onWillDisplay(any()) }
        }

        test("onMessageWillDisplay does nothing when no subscribers") {
            // Given

            // When/Then - should not throw
            mocks.inAppMessagesManager.onMessageWillDisplay(mocks.testInAppMessage)

            // Verified by no exception being thrown when no listeners are subscribed
        }

        test("onMessageWasDisplayed sends impression for non-preview message") {
            // Given

            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery { mocks.backend.sendIAMImpression(any(), any(), any(), any()) } just runs

            // When
            mocks.inAppMessagesManager.onMessageWasDisplayed(mocks.testInAppMessage)

            // Then
            coVerify { mocks.backend.sendIAMImpression(any(), any(), any(), any()) }
        }

        test("onMessageWasDisplayed does not send impression for preview message") {
            // Given

            // When
            mocks.inAppMessagesManager.onMessageWasDisplayed(mocks.testInAppMessagePreview)

            // Then
            coVerify(exactly = 0) { mocks.backend.sendIAMImpression(any(), any(), any(), any()) }
        }

        test("onMessageWasDisplayed does not send duplicate impressions") {
            // Given
            val message = mocks.testInAppMessage
            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery { mocks.backend.sendIAMImpression(any(), any(), any(), any()) } just runs

            // When - send impression twice
            runBlocking {
                mocks.inAppMessagesManager.onMessageWasDisplayed(message)
                mocks.inAppMessagesManager.onMessageWasDisplayed(message)
            }

            // Then - should only send once
            coVerify(exactly = 1) { mocks.backend.sendIAMImpression(any(), any(), any(), any()) }
        }

        test("onMessageWillDismiss fires lifecycle callback when subscribers exist") {
            // Given
            mocks.inAppMessagesManager.addLifecycleListener(mocks.inAppMessageLifecycleListener)

            // When
            mocks.inAppMessagesManager.onMessageWillDismiss(mocks.testInAppMessage)

            // Then
            // Verify callback was fired
            verify { mocks.inAppMessageLifecycleListener.onWillDismiss(any()) }
        }

        test("onMessageWillDismiss does nothing when no subscribers") {
            // Given

            // When/Then - should not throw
            mocks.inAppMessagesManager.onMessageWillDismiss(mocks.testInAppMessage)

            // Verified by no exception being thrown when no listeners are subscribed
        }

        test("onMessageWasDismissed calls messageWasDismissed") {
            // Given
            every { mocks.inAppStateService.inAppMessageIdShowing } returns null

            // When
            mocks.inAppMessagesManager.onMessageWasDismissed(mocks.testInAppMessage)
            awaitIO()

            // Then
            verify { mocks.influenceManager.onInAppMessageDismissed() }
        }
    }

    context("Trigger Callbacks") {
        test("onTriggerCompleted does nothing") {
            // Given
            val iamManager = mocks.inAppMessagesManager

            // When/Then - should not throw
            iamManager.onTriggerCompleted("trigger-id")

            // Verified by no exception being thrown (method is a no-op)
        }

        test("onTriggerConditionChanged makes redisplay messages available and re-evaluates") {
            // Given
            val message = mocks.testInAppMessage
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns listOf(message)

            // Fetch messages first
            mocks.inAppMessagesManager.onSessionStarted()
            awaitIO()

            // When
            mocks.inAppMessagesManager.onTriggerConditionChanged("trigger-id")

            // Then
            // Should trigger re-evaluation
            verify { mocks.triggerController.evaluateMessageTriggers(any()) }
        }

        test("onTriggerChanged makes redisplay messages available and re-evaluates") {
            // Given
            val message = mocks.testInAppMessage
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns listOf(message)

            // Fetch messages first
            mocks.inAppMessagesManager.onSessionStarted()
            awaitIO()

            // When
            mocks.inAppMessagesManager.onTriggerChanged("trigger-key")

            // Then
            // Should trigger re-evaluation
            verify { mocks.triggerController.evaluateMessageTriggers(any()) }
        }
    }

    context("Application Lifecycle") {
        test("onFocus does nothing") {
            // Given

            // When/Then - should not throw
            mocks.inAppMessagesManager.onFocus(false)
            mocks.inAppMessagesManager.onFocus(true)
        }
        test("onUnfocused does nothing") {
            // Given

            // When/Then - should not throw
            mocks.inAppMessagesManager.onUnfocused()

            // Verified by no exception being thrown
        }
    }

    context("Message Action Handling") {
        test("onMessageActionOccurredOnPreview processes preview actions") {
            // Given
            val mockClickListener = mockk<IInAppMessageClickListener>(relaxed = true)
            val mockPrompt = mockk<InAppMessagePrompt>(relaxed = true)
            every { mockPrompt.hasPrompted() } returns false
            coEvery { mockPrompt.handlePrompt() } returns InAppMessagePrompt.PromptActionResult.PERMISSION_GRANTED
            every { mocks.inAppStateService.currentPrompt } returns null
            mocks.inAppMessagesManager.addClickListener(mockClickListener)

            // When
            mocks.inAppMessagesManager.onMessageActionOccurredOnPreview(mocks.testInAppMessagePreview, mocks.inAppMessageClickResult)

            // Then
            verify { mocks.inAppMessageClickResult.isFirstClick = any() }
        }

        test("onMessagePageChanged sends page impression for non-preview message") {
            // Given
            val mockPage = mockk<InAppMessagePage>(relaxed = true)
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mockPage.pageId } returns "page-id"
            coEvery { mocks.backend.sendIAMPageImpression(any(), any(), any(), any(), any()) } just runs

            // When
            mocks.inAppMessagesManager.onMessagePageChanged(mocks.testInAppMessage, mockPage)

            // Then
            coVerify { mocks.backend.sendIAMPageImpression(any(), any(), any(), any(), any()) }
        }

        test("onMessagePageChanged does nothing for preview message") {
            // Given
            val mockPage = mockk<InAppMessagePage>(relaxed = true)

            // When
            mocks.inAppMessagesManager.onMessagePageChanged(mocks.testInAppMessagePreview, mockPage)

            // Then
            coVerify(exactly = 0) { mocks.backend.sendIAMPageImpression(any(), any(), any(), any(), any()) }
        }
    }

    context("Error Handling") {
        test("onMessageWasDisplayed removes impression from set on backend failure") {
            // Given
            val message = mocks.testInAppMessage
            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery {
                mocks.backend.sendIAMImpression(any(), any(), any(), any())
            } throws BackendException(500, "Server error")

            // When
            mocks.inAppMessagesManager.onMessageWasDisplayed(message)
            awaitIO()

            // Try again - should retry since impression was removed
            mocks.inAppMessagesManager.onMessageWasDisplayed(message)
            awaitIO()

            // Then - should attempt twice since first failed
            coVerify(exactly = 2) { mocks.backend.sendIAMImpression(any(), any(), any(), any()) }
        }

        test("onMessagePageChanged removes page impression on backend failure") {
            // Given
            val message = mocks.testInAppMessage
            val mockPage = mockk<InAppMessagePage>(relaxed = true)
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mockPage.pageId } returns "page-id"
            coEvery {
                mocks.backend.sendIAMPageImpression(any(), any(), any(), any(), any())
            } throws BackendException(500, "Server error")

            // When
            mocks.inAppMessagesManager.onMessagePageChanged(message, mockPage)
            awaitIO()

            // Try again - should retry since page impression was removed
            mocks.inAppMessagesManager.onMessagePageChanged(message, mockPage)
            awaitIO()

            // Then - should attempt twice since first failed
            coVerify(exactly = 2) { mocks.backend.sendIAMPageImpression(any(), any(), any(), any(), any()) }
        }

        test("onMessageActionOccurredOnMessage removes click on backend failure") {
            // Given
            val message = mocks.testInAppMessage
            coEvery {
                mocks.backend.sendIAMClick(any(), any(), any(), any(), any(), any())
            } throws BackendException(500, "Server error")

            // When
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(message, mocks.inAppMessageClickResult)
            awaitIO()

            // Then
            coVerify { mocks.backend.sendIAMClick(any(), any(), any(), any(), any(), any()) }
            // Click should be removed from message on failure
            message.isClickAvailable("click-id") shouldBe true
        }
    }

    context("Message Fetching") {
        test("fetchMessagesWhenConditionIsMet returns early when app is not in foreground") {
            // Given
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns false

            // When - trigger fetch via onSessionStarted
            mocks.inAppMessagesManager.onSessionStarted()

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("fetchMessagesWhenConditionIsMet returns early when subscriptionId is empty") {
            // Given
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns ""

            // When
            mocks.inAppMessagesManager.onSessionStarted()

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("fetchMessagesWhenConditionIsMet returns early when subscriptionId is local ID") {
            // Given
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns "local-123"

            // When
            mocks.inAppMessagesManager.onSessionStarted()

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("fetchMessagesWhenConditionIsMet evaluates messages when new messages are returned") {
            // Given
            val message = mocks.testInAppMessage
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mocks.triggerController.evaluateMessageTriggers(any()) } returns false
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns listOf(message)

            // When
            mocks.inAppMessagesManager.onSessionStarted()

            // Then
            coVerify { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
            verify { mocks.triggerController.evaluateMessageTriggers(any()) }
        }
    }

    context("Message Queue and Display") {
        test("messages are not queued when paused") {
            // Given
            val message = mocks.testInAppMessage
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mocks.triggerController.evaluateMessageTriggers(message) } returns true
            every { mocks.triggerController.isTriggerOnMessage(any(), any()) } returns false
            every { mocks.triggerController.messageHasOnlyDynamicTriggers(any()) } returns false
            every { mocks.inAppStateService.inAppMessageIdShowing } returns null
            every { mocks.inAppStateService.paused } returns true

            // When - fetch messages while paused
            mocks.inAppMessagesManager.onSessionStarted()

            // Then - should not display
            coVerify(exactly = 0) { mocks.inAppDisplayer.displayMessage(any()) }
        }
    }

    context("Message Evaluation") {
        test("messages are evaluated and queued when paused is set to false") {
            // Given
            val message = mocks.testInAppMessage
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mocks.triggerController.evaluateMessageTriggers(message) } returns true
            every { mocks.triggerController.isTriggerOnMessage(any(), any()) } returns false
            every { mocks.triggerController.messageHasOnlyDynamicTriggers(any()) } returns false
            every { mocks.inAppStateService.inAppMessageIdShowing } returns null
            every { mocks.inAppStateService.paused } returns true
            coEvery { mocks.applicationService.waitUntilSystemConditionsAvailable() } returns true
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns listOf(message)
            coEvery { mocks.inAppDisplayer.displayMessage(any()) } returns true

            // Fetch messages first
            mocks.inAppMessagesManager.onSessionStarted()

            // When - set paused to false, which triggers evaluateInAppMessages
            mocks.inAppMessagesManager.paused = false

            // Then
            verify { mocks.triggerController.evaluateMessageTriggers(message) }
        }

        test("dismissed messages are not queued for display") {
            // Given
            val message = mocks.testInAppMessage
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mocks.triggerController.evaluateMessageTriggers(message) } returns true
            every { mocks.triggerController.isTriggerOnMessage(any(), any()) } returns false
            every { mocks.triggerController.messageHasOnlyDynamicTriggers(any()) } returns false
            every { mocks.inAppStateService.paused } returns false

            // Fetch messages
            mocks.inAppMessagesManager.onSessionStarted()

            // Dismiss the message
            mocks.inAppMessagesManager.onMessageWasDismissed(message)

            // When - trigger evaluation
            mocks.inAppMessagesManager.paused = false

            // Then - should not display dismissed message
            coVerify(exactly = 0) { mocks.inAppDisplayer.displayMessage(message) }
        }
    }

    context("Message Actions - Outcomes and Tags") {
        test("onMessageActionOccurredOnMessage fires outcomes") {
            // Given

            // When
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.testInAppMessage, mocks.inAppMessageClickResult)

            // Then - wait for async operations
            coVerify { mocks.outcomeEventsController.sendOutcomeEvent("outcome-name") }
        }

        test("onMessageActionOccurredOnMessage fires outcomes with weight") {
            // Given
            val weight = 5.0f
            every { mocks.testOutcome.weight } returns weight

            // When
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.testInAppMessage, mocks.inAppMessageClickResult)

            // Then - wait for async operations
            coVerify { mocks.outcomeEventsController.sendOutcomeEventWithValue("outcome-name", weight) }
        }

        test("onMessageActionOccurredOnMessage adds tags") {
            // Given
            val mockTags = mockk<InAppMessageTag>(relaxed = true)
            val tagsToAdd = JSONObject()
            tagsToAdd.put("key1", "value1")
            every { mockTags.tagsToAdd } returns tagsToAdd
            every { mockTags.tagsToRemove } returns null
            every { mocks.inAppMessageClickResult.tags } returns mockTags

            // When
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.testInAppMessage, mocks.inAppMessageClickResult)
            awaitIO()

            // Then - wait for async operations
            verify { mocks.userManager.addTags(any()) }
        }

        test("onMessageActionOccurredOnMessage removes tags") {
            // Given
            val mockTags = mockk<InAppMessageTag>(relaxed = true)
            val tagsToRemove = JSONArray()
            tagsToRemove.put("key1")
            every { mockTags.tagsToAdd } returns null
            every { mockTags.tagsToRemove } returns tagsToRemove
            every { mocks.inAppMessageClickResult.tags } returns mockTags

            // When
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.testInAppMessage, mocks.inAppMessageClickResult)
            awaitIO()

            // Then - wait for async operations
            coVerify { mocks.userManager.removeTags(any()) }
        }

        test("onMessageActionOccurredOnMessage opens URL in browser") {
            // Given
            val url = "https://example.com"
            val mockContext = mockk<Context>(relaxed = true)
            every { mocks.applicationService.appContext } returns mockContext
            every { mocks.inAppMessageClickResult.url } returns url
            every { mocks.inAppMessageClickResult.urlTarget } returns InAppMessageActionUrlType.BROWSER
            mockkObject(AndroidUtils)
            every { AndroidUtils.openURLInBrowser(any<Context>(), any<String>()) } just runs

            // When
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.testInAppMessage, mocks.inAppMessageClickResult)

            // Then
            coVerify { AndroidUtils.openURLInBrowser(any<Context>(), url) }

            unmockkObject(AndroidUtils)
        }

        test("onMessageActionOccurredOnMessage opens URL in webview") {
            // Given
            val mockContext = mockk<Context>(relaxed = true)
            every { mocks.applicationService.appContext } returns mockContext
            every { mocks.inAppMessageClickResult.url } returns "https://example.com"
            every { mocks.inAppMessageClickResult.urlTarget } returns InAppMessageActionUrlType.IN_APP_WEBVIEW
            mockkObject(OneSignalChromeTab)
            every { OneSignalChromeTab.open(any(), any(), any()) } returns true

            // When
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.testInAppMessage, mocks.inAppMessageClickResult)

            // Then
            coVerify { OneSignalChromeTab.open("https://example.com", true, any()) }

            unmockkObject(OneSignalChromeTab)
        }

        test("onMessageActionOccurredOnMessage does nothing when URL is empty") {
            // Given

            // When/Then - should not throw
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.testInAppMessage, mocks.inAppMessageClickResult)
        }
    }
    context("Prompt Processing") {
        test("onMessageActionOccurredOnMessage processes prompts") {
            // Given
            val mockPrompt = mockk<InAppMessagePrompt>(relaxed = true)
            every { mocks.inAppMessageClickResult.prompts } returns mutableListOf(mockPrompt)
            every { mockPrompt.hasPrompted() } returns false
            every { mockPrompt.setPrompted(any()) } just runs
            // currentPrompt starts as null, then gets set to the prompt during processing
            var currentPrompt: InAppMessagePrompt? = null
            every { mocks.inAppStateService.currentPrompt } answers { currentPrompt }
            every { mocks.inAppStateService.currentPrompt = any() } answers { currentPrompt = firstArg() }

            // When
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.testInAppMessage, mocks.inAppMessageClickResult)

            // Then
            coVerify { mocks.inAppDisplayer.dismissCurrentInAppMessage() }
            coVerify { mockPrompt.setPrompted(any()) }
        }

        test("onMessageActionOccurredOnMessage does nothing when prompts list is empty") {
            // Given

            // When
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.testInAppMessage, mocks.inAppMessageClickResult)

            // Then
            coVerify(exactly = 0) { mocks.inAppDisplayer.dismissCurrentInAppMessage() }
        }
    }

    context("Message Persistence") {
        test("onMessageWasDismissed persists message to repository") {
            // Given
            val message = mocks.testInAppMessage
            coEvery { mocks.repository.saveInAppMessage(any()) } just runs
            every { mocks.inAppStateService.lastTimeInAppDismissed } returns 500L
            every { mocks.inAppStateService.currentPrompt } returns null

            // When
            mocks.inAppMessagesManager.onMessageWasDismissed(message)

            // Then
            coVerify { mocks.repository.saveInAppMessage(message) }
            message.isDisplayedInSession shouldBe true
            message.isTriggerChanged shouldBe false
        }
    }
})
