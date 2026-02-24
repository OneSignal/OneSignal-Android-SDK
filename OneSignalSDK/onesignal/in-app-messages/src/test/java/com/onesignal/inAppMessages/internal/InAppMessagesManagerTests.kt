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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

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
    val testInAppMessagePreview: InAppMessage
        get() = InAppMessage(true, time)

    // Helper function to create InAppMessage with custom triggers (factory-style, returns new message each call)
    fun createInAppMessage(
        id: String = "test-message-${System.nanoTime()}", // Unique ID by default
        triggers: List<Triple<String, String, String>> = emptyList() // List of (property, operator, value)
    ): InAppMessage {
        val json = JSONObject().apply {
            put("id", id)
            put("variants", JSONObject().apply {
                put("all", JSONObject().apply { put("en", "variant-id-123") })
            })

            if (triggers.isEmpty()) {
                put("triggers", JSONArray())
            } else {
                put("triggers", JSONArray().apply {
                    put(JSONArray().apply {
                        triggers.forEach { (property, operator, value) ->
                            put(JSONObject().apply {
                                put("id", "trigger-$property")
                                put("kind", "custom")
                                put("property", property)
                                put("operator", operator)
                                put("value", value)
                            })
                        }
                    })
                })
            }
        }
        return InAppMessage(json, time)
    }

    // Helper function to access private earlySessionTriggers field for testing using Kotlin reflection
    fun getEarlySessionTriggers(manager: InAppMessagesManager): MutableSet<String> {
        val property = InAppMessagesManager::class.memberProperties
            .first { it.name == "earlySessionTriggers" }
        property.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return property.get(manager) as MutableSet<String>
    }

    // Helper function to access private hasCompletedFirstFetch field for testing using Kotlin reflection
    fun getHasCompletedFirstFetch(manager: InAppMessagesManager): Boolean {
        val property = InAppMessagesManager::class.memberProperties
            .first { it.name == "hasCompletedFirstFetch" }
        property.isAccessible = true
        return property.get(manager) as Boolean
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
            triggerModelSlots.map { it.key to it.value } shouldBe listOf(
                "trigger-key1" to "trigger-value1",
                "trigger-key2" to "trigger-value2",
                "trigger-key3" to "trigger-value3",
            )
            verify(exactly = 1) {
                mockTriggerModelStore.remove("trigger-key4")
                mockTriggerModelStore.remove("trigger-key5")
                mockTriggerModelStore.remove("trigger-key6")
                mockTriggerModelStore.clear()
            }
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
            val message1 = mocks.createInAppMessage()
            val message2 = mocks.createInAppMessage()
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
            awaitIO()

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
            iamManager.onMessageWillDisplay(mocks.createInAppMessage())

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
            iamManager.onMessageWillDisplay(mocks.createInAppMessage())

            // Then
            // Listener should not be called after removal
            verify(exactly = 0) { mockListener.onWillDisplay(any()) }
        }

        test("addClickListener subscribes listener") {
            // Given
            val mockListener = mocks.inAppMessageClickListener
            val message = mocks.createInAppMessage()
            val mockClickResult = mocks.inAppMessageClickResult
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.addClickListener(mockListener)
            iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)
            awaitIO()

            // Then
            // Verify listener callback was called
            verify { mockListener.onClick(any()) }
        }

        test("removeClickListener unsubscribes listener") {
            // Given
            val mockListener = mockk<IInAppMessageClickListener>(relaxed = true)
            val message = mocks.createInAppMessage()
            val mockClickResult = mocks.inAppMessageClickResult
            val iamManager = mocks.inAppMessagesManager

            // When
            iamManager.addClickListener(mockListener)
            iamManager.removeClickListener(mockListener)
            iamManager.onMessageActionOccurredOnMessage(message, mockClickResult)
            awaitIO()

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
            awaitIO()

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
            awaitIO()

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
            awaitIO()

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
            awaitIO()

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
            val message1 = mocks.createInAppMessage()
            val message2 = mocks.createInAppMessage()
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
            mocks.inAppMessagesManager.onMessageWillDisplay(mocks.createInAppMessage())
            awaitIO()

            // Then
            // Verify callback was fired
            verify { mocks.inAppMessageLifecycleListener.onWillDisplay(any()) }
        }

        test("onMessageWillDisplay does nothing when no subscribers") {
            // Given

            // When/Then - should not throw
            mocks.inAppMessagesManager.onMessageWillDisplay(mocks.createInAppMessage())

            // Verified by no exception being thrown when no listeners are subscribed
        }

        test("onMessageWasDisplayed sends impression for non-preview message") {
            // Given

            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery { mocks.backend.sendIAMImpression(any(), any(), any(), any()) } just runs

            // When
            mocks.inAppMessagesManager.onMessageWasDisplayed(mocks.createInAppMessage())
            awaitIO()

            // Then
            coVerify { mocks.backend.sendIAMImpression(any(), any(), any(), any()) }
        }

        test("onMessageWasDisplayed does not send impression for preview message") {
            // Given

            // When
            mocks.inAppMessagesManager.onMessageWasDisplayed(mocks.testInAppMessagePreview)
            awaitIO()

            // Then
            coVerify(exactly = 0) { mocks.backend.sendIAMImpression(any(), any(), any(), any()) }
        }

        test("onMessageWasDisplayed does not send duplicate impressions") {
            // Given
            val message = mocks.createInAppMessage()
            every { mocks.pushSubscription.id } returns "subscription-id"
            coEvery { mocks.backend.sendIAMImpression(any(), any(), any(), any()) } just runs

            // When - send impression twice
            mocks.inAppMessagesManager.onMessageWasDisplayed(message)
            mocks.inAppMessagesManager.onMessageWasDisplayed(message)
            awaitIO()

            // Then - should only send once
            coVerify(exactly = 1) { mocks.backend.sendIAMImpression(any(), any(), any(), any()) }
        }

        test("onMessageWillDismiss fires lifecycle callback when subscribers exist") {
            // Given
            mocks.inAppMessagesManager.addLifecycleListener(mocks.inAppMessageLifecycleListener)

            // When
            mocks.inAppMessagesManager.onMessageWillDismiss(mocks.createInAppMessage())
            awaitIO()

            // Then
            // Verify callback was fired
            verify { mocks.inAppMessageLifecycleListener.onWillDismiss(any()) }
        }

        test("onMessageWillDismiss does nothing when no subscribers") {
            // Given

            // When/Then - should not throw
            mocks.inAppMessagesManager.onMessageWillDismiss(mocks.createInAppMessage())

            // Verified by no exception being thrown when no listeners are subscribed
        }

        test("onMessageWasDismissed calls messageWasDismissed") {
            // Given
            every { mocks.inAppStateService.inAppMessageIdShowing } returns null

            // When
            mocks.inAppMessagesManager.onMessageWasDismissed(mocks.createInAppMessage())
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
            val message = mocks.createInAppMessage()
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
            coVerify { mocks.triggerController.evaluateMessageTriggers(any()) }
        }

        test("onTriggerChanged makes redisplay messages available and re-evaluates") {
            // Given
            val message = mocks.createInAppMessage()
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
            awaitIO()

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
            mocks.inAppMessagesManager.onMessagePageChanged(mocks.createInAppMessage(), mockPage)
            awaitIO()

            // Then
            coVerify { mocks.backend.sendIAMPageImpression(any(), any(), any(), any(), any()) }
        }

        test("onMessagePageChanged does nothing for preview message") {
            // Given
            val mockPage = mockk<InAppMessagePage>(relaxed = true)

            // When
            mocks.inAppMessagesManager.onMessagePageChanged(mocks.testInAppMessagePreview, mockPage)
            awaitIO()

            // Then
            coVerify(exactly = 0) { mocks.backend.sendIAMPageImpression(any(), any(), any(), any(), any()) }
        }
    }

    context("Error Handling") {
        test("onMessageWasDisplayed removes impression from set on backend failure") {
            // Given
            val message = mocks.createInAppMessage()
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
            val message = mocks.createInAppMessage()
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
            val message = mocks.createInAppMessage()
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
            awaitIO()

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
            awaitIO()

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
            awaitIO()

            // Then
            coVerify(exactly = 0) { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
        }

        test("fetchMessagesWhenConditionIsMet evaluates messages when new messages are returned") {
            // Given
            val message = mocks.createInAppMessage()
            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mocks.triggerController.evaluateMessageTriggers(any()) } returns false
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns listOf(message)

            // When
            mocks.inAppMessagesManager.onSessionStarted()
            awaitIO()

            // Then
            coVerify { mocks.backend.listInAppMessages(any(), any(), any(), any()) }
            verify { mocks.triggerController.evaluateMessageTriggers(any()) }
        }
    }

    context("Message Queue and Display") {
        test("messages are not queued when paused") {
            // Given
            val message = mocks.createInAppMessage()
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
            awaitIO()

            // Then - should not display
            coVerify(exactly = 0) { mocks.inAppDisplayer.displayMessage(any()) }
        }
    }

    context("Message Evaluation") {
        test("messages are evaluated and queued when paused is set to false") {
            // Given
            val message = mocks.createInAppMessage()
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
            awaitIO()

            // When - set paused to false, which triggers evaluateInAppMessages
            mocks.inAppMessagesManager.paused = false

            // Then
            verify { mocks.triggerController.evaluateMessageTriggers(message) }
        }

        test("dismissed messages are not queued for display") {
            // Given
            val message = mocks.createInAppMessage()
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
            awaitIO()

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
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.createInAppMessage(), mocks.inAppMessageClickResult)
            awaitIO()

            // Then - wait for async operations
            coVerify { mocks.outcomeEventsController.sendOutcomeEvent("outcome-name") }
        }

        test("onMessageActionOccurredOnMessage fires outcomes with weight") {
            // Given
            val weight = 5.0f
            every { mocks.testOutcome.weight } returns weight

            // When
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.createInAppMessage(), mocks.inAppMessageClickResult)
            awaitIO()

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
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.createInAppMessage(), mocks.inAppMessageClickResult)
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
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.createInAppMessage(), mocks.inAppMessageClickResult)
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
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.createInAppMessage(), mocks.inAppMessageClickResult)
            awaitIO()

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
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.createInAppMessage(), mocks.inAppMessageClickResult)
            awaitIO()

            // Then
            coVerify { OneSignalChromeTab.open("https://example.com", true, any()) }

            unmockkObject(OneSignalChromeTab)
        }

        test("onMessageActionOccurredOnMessage does nothing when URL is empty") {
            // Given

            // When/Then - should not throw
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.createInAppMessage(), mocks.inAppMessageClickResult)
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
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.createInAppMessage(), mocks.inAppMessageClickResult)
            awaitIO()

            // Then
            coVerify { mocks.inAppDisplayer.dismissCurrentInAppMessage() }
            coVerify { mockPrompt.setPrompted(any()) }
        }

        test("onMessageActionOccurredOnMessage does nothing when prompts list is empty") {
            // Given

            // When
            mocks.inAppMessagesManager.onMessageActionOccurredOnMessage(mocks.createInAppMessage(), mocks.inAppMessageClickResult)
            awaitIO()

            // Then
            coVerify(exactly = 0) { mocks.inAppDisplayer.dismissCurrentInAppMessage() }
        }
    }

    context("Message Persistence") {
        test("onMessageWasDismissed persists message to repository") {
            // Given
            val message = mocks.createInAppMessage()
            coEvery { mocks.repository.saveInAppMessage(any()) } just runs
            every { mocks.inAppStateService.lastTimeInAppDismissed } returns 500L
            every { mocks.inAppStateService.currentPrompt } returns null

            // When
            mocks.inAppMessagesManager.onMessageWasDismissed(message)
            awaitIO()

            // Then
            coVerify { mocks.repository.saveInAppMessage(message) }
            message.isDisplayedInSession shouldBe true
            message.isTriggerChanged shouldBe false
        }
    }

    context("Early Trigger Tracking") {
        test("triggers added before first fetch are tracked in earlySessionTriggers") {
            // Given
            val iamManager = mocks.inAppMessagesManager
            every { mocks.triggerModelStore.get(any()) } returns null
            every { mocks.triggerModelStore.add(any()) } answers {}

            // When
            iamManager.addTrigger("trigger1", "value1")
            iamManager.addTrigger("trigger2", "value2")

            // Then
            // Verify triggers were added to the triggerModelStore
            verify(exactly = 1) { mocks.triggerModelStore.add(match { it.key == "trigger1" && it.value == "value1" }) }
            verify(exactly = 1) { mocks.triggerModelStore.add(match { it.key == "trigger2" && it.value == "value2" }) }

            // Verify triggers were tracked in earlySessionTriggers
            val earlySessionTriggers = mocks.getEarlySessionTriggers(iamManager)
            earlySessionTriggers.contains("trigger1") shouldBe true
            earlySessionTriggers.contains("trigger2") shouldBe true
        }

        test("messages matching early triggers get isTriggerChanged flag after first fetch") {
            // Given
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mocks.triggerModelStore.get(any()) } returns null
            every { mocks.triggerModelStore.add(any()) } answers {}

            // Create test messages
            // message1 has a trigger matching the early trigger we'll add
            val message1 = mocks.createInAppMessage(
                id = "message-1",
                triggers = listOf(Triple("earlyTrigger", "equal", "value"))
            )

            // message2 has a different trigger that doesn't match
            val message2 = mocks.createInAppMessage(
                id = "message-2",
                triggers = listOf(Triple("otherTrigger", "equal", "value"))
            )

            // Add message1 to redisplayedInAppMessages (simulate it was previously shown)
            val redisplayedMessages = mutableListOf(message1)
            coEvery { mocks.repository.listInAppMessages() } returns redisplayedMessages

            // Mock trigger controller to say message1 matches early trigger, message2 does not
            every { mocks.triggerController.isTriggerOnMessage(message1, any<Collection<String>>()) } returns true
            every { mocks.triggerController.isTriggerOnMessage(message2, any<Collection<String>>()) } returns false
            every { mocks.triggerController.evaluateMessageTriggers(any()) } returns false

            // Mock backend to return both messages
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns listOf(message1, message2)

            // Start the manager to load redisplayed messages
            mocks.inAppMessagesManager.start()
            awaitIO()

            // Add triggers before first fetch
            mocks.inAppMessagesManager.addTrigger("earlyTrigger", "value")

            // Both messages start with isTriggerChanged = false
            message1.isTriggerChanged shouldBe false
            message2.isTriggerChanged shouldBe false

            // When - Trigger first fetch
            mocks.inAppMessagesManager.onSessionStarted()
            awaitIO()

            // Message 1 should have isTriggerChanged = true (matches early trigger and was redisplayed)
            message1.isTriggerChanged shouldBe true

            // Message 2 should have isTriggerChanged = false (does not match early trigger)
            message2.isTriggerChanged shouldBe false
        }

        test("triggers added after first fetch are not tracked as early triggers") {
            // Given
            val message = mocks.createInAppMessage(
                id = "message-1",
                triggers = listOf(Triple("lateTrigger", "equal", "value"))
            )

            every { mocks.userManager.onesignalId } returns "onesignal-id"
            every { mocks.applicationService.isInForeground } returns true
            every { mocks.pushSubscription.id } returns "subscription-id"
            every { mocks.configModelStore.model.appId } returns "test-app-id"
            every { mocks.configModelStore.model.fetchIAMMinInterval } returns 0L
            every { mocks.triggerModelStore.get(any()) } returns null
            every { mocks.triggerModelStore.add(any()) } answers {}

            // Message is in redisplayedInAppMessages from the start
            coEvery { mocks.repository.listInAppMessages() } returns mutableListOf(message)

            // Mock trigger controller to say message matches the late trigger
            every { mocks.triggerController.isTriggerOnMessage(message, any<Collection<String>>()) } returns true
            every { mocks.triggerController.evaluateMessageTriggers(any()) } returns false

            // Mock first fetch to return the message
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns listOf(message)

            mocks.inAppMessagesManager.start()
            awaitIO()

            // Trigger first fetch (this sets hasCompletedFirstFetch = true)
            mocks.inAppMessagesManager.onSessionStarted()
            awaitIO()

            // Verify first fetch completed
            mocks.getHasCompletedFirstFetch(mocks.inAppMessagesManager) shouldBe true

            // When - Add trigger AFTER first fetch (should NOT be tracked)
            mocks.inAppMessagesManager.addTrigger("lateTrigger", "value")

            // Verify trigger was NOT added to earlySessionTriggers
            val earlySessionTriggers = mocks.getEarlySessionTriggers(mocks.inAppMessagesManager)
            earlySessionTriggers.contains("lateTrigger") shouldBe false

            // Mock second fetch to return the same message
            coEvery { mocks.backend.listInAppMessages(any(), any(), any(), any()) } returns listOf(message)

            // Trigger second fetch
            mocks.inAppMessagesManager.onSessionStarted()
            awaitIO()

            // Then
            // Message should NOT have isTriggerChanged because trigger was added after first fetch
            message.isTriggerChanged shouldBe false
        }

        test("earlySessionTriggers is cleared after first fetch") {
            // Given
            val mockTriggerModelStore = mocks.triggerModelStore
            val mockBackend = mocks.backend
            val mockRepository = mocks.repository
            val mockTriggerController = mocks.triggerController
            val iamManager = mocks.inAppMessagesManager

            every { mockTriggerModelStore.get(any()) } returns null
            every { mockTriggerModelStore.add(any()) } answers {}
            coEvery { mockRepository.listInAppMessages() } returns mutableListOf()
            every { mockTriggerController.evaluateMessageTriggers(any()) } returns false
            coEvery { mockBackend.listInAppMessages(any(), any(), any(), any()) } returns listOf(mocks.createInAppMessage())

            every { mocks.pushSubscription.id } returns "test-sub-id"
            every { mocks.configModelStore.model.appId } returns "test-app-id"
            every { mocks.configModelStore.model.fetchIAMMinInterval } returns 0L
            every { mocks.applicationService.isInForeground } returns true

            iamManager.start()
            awaitIO()

            // Add triggers before first fetch
            iamManager.addTrigger("trigger1", "value1")
            iamManager.addTrigger("trigger2", "value2")

            // Verify triggers were tracked before first fetch
            val earlySessionTriggersBeforeFetch = mocks.getEarlySessionTriggers(iamManager)
            earlySessionTriggersBeforeFetch.size shouldBe 2
            earlySessionTriggersBeforeFetch.contains("trigger1") shouldBe true
            earlySessionTriggersBeforeFetch.contains("trigger2") shouldBe true

            // When - Trigger first fetch
            mocks.inAppMessagesManager.onSessionStarted()
            awaitIO()

            // Verify earlySessionTriggers was cleared after first fetch
            val earlySessionTriggersAfterFetch = mocks.getEarlySessionTriggers(iamManager)
            earlySessionTriggersAfterFetch.size shouldBe 0

            // Create a message for potential second fetch
            val messageAfterClear = mocks.createInAppMessage()

            // Mock backend for second fetch
            coEvery { mockBackend.listInAppMessages(any(), any(), any(), any()) } returns listOf(messageAfterClear)

            // Mock that message is in redisplayed and matches the cleared triggers
            coEvery { mockRepository.listInAppMessages() } returns mutableListOf(messageAfterClear)
            every { mockTriggerController.isTriggerOnMessage(messageAfterClear, any<Collection<String>>()) } returns true

            // Trigger second fetch
            mocks.inAppMessagesManager.onSessionStarted()
            awaitIO()

            // Then
            // Message should NOT have isTriggerChanged because earlySessionTriggers was cleared after first fetch
            messageAfterClear.isTriggerChanged shouldBe false
        }
    }
})
