package com.onesignal.session.internal.session

import com.onesignal.common.IDManager
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.runOnSerialIO
import com.onesignal.core.internal.application.AppEntryAction
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.features.FeatureFlag
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.session.internal.session.impl.SessionService
import com.onesignal.session.internal.session.operations.CreateSessionOperation
import com.onesignal.session.internal.session.operations.UpdateSessionOperation
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.jwt.JwtRequirement
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

// Mocks used by every test in this file
private class Mocks(
    var sessionsApiEnabled: Boolean = false,
    externalId: String? = EXTERNAL_ID,
) {
    var currentTime = 1111L
    var elapsedRealtime = 5000L

    private val mockSessionModelStore = MockHelper.sessionModelStore()

    // Runs the heartbeat in virtual time; elapsedRealtime advances with it.
    val heartbeatScope = TestScope()

    val time: ITime =
        mockk<ITime>().also {
            every { it.currentTimeMillis } answers { currentTime }
            every { it.elapsedRealtimeMillis } answers { elapsedRealtime + heartbeatScope.testScheduler.currentTime }
        }

    val featureManager: IFeatureManager =
        mockk<IFeatureManager>().also {
            every { it.isEnabled(FeatureFlag.SDK_SESSIONS_V2_API_CUTOVER) } answers { sessionsApiEnabled }
        }

    val identityModelStore =
        MockHelper.identityModelStore {
            it.onesignalId = ONESIGNAL_ID
            it.externalId = externalId
        }
    val configModelStore = MockHelper.configModelStore { it.pushSubscriptionId = SUBSCRIPTION_ID }

    val identityChangeHandler = slot<ISingletonModelStoreChangeHandler<IdentityModel>>()
    val configChangeHandlers = mutableListOf<ISingletonModelStoreChangeHandler<ConfigModel>>()

    init {
        every { identityModelStore.subscribe(capture(identityChangeHandler)) } just runs
        every { configModelStore.subscribe(capture(configChangeHandlers)) } just runs
    }

    /** Mirrors the user-create executor replacing a local ID with the backend ID in place. */
    fun hydrateOnesignalId(backendId: String) {
        val model = identityModelStore.model
        val localId = model.onesignalId
        model.onesignalId = backendId
        identityChangeHandler.captured.onModelUpdated(
            ModelChangedArgs(model, IdentityConstants.ONESIGNAL_ID, IdentityConstants.ONESIGNAL_ID, localId, backendId),
            ModelChangeTags.HYDRATE,
        )
    }

    fun hydratePushSubscriptionId(backendId: String) {
        val model = configModelStore.model
        val localId = model.pushSubscriptionId
        model.pushSubscriptionId = backendId
        notifyConfigChanged(ConfigModel::pushSubscriptionId.name, localId, backendId)
    }

    /** Mirrors a login or logout replacing the current user. The push subscription keeps its ID. */
    fun switchUser(
        onesignalId: String,
        externalId: String?,
    ) {
        identityModelStore.model.onesignalId = onesignalId
        identityModelStore.model.externalId = externalId
    }

    /** Mirrors `OneSignal.consentGiven` with consent required. */
    fun setConsentGiven(given: Boolean) {
        val model = configModelStore.model
        model.consentRequired = true
        val oldValue = model.consentGiven
        model.consentGiven = given
        notifyConfigChanged(ConfigModel::consentGiven.name, oldValue, given)
    }

    /** Mirrors a params fetch replacing the config model in place. */
    fun replaceConfig() {
        configChangeHandlers.forEach { it.onModelReplaced(configModelStore.model, ModelChangeTags.HYDRATE) }
    }

    private fun notifyConfigChanged(
        property: String,
        oldValue: Any?,
        newValue: Any?,
    ) {
        val args = ModelChangedArgs(configModelStore.model, property, property, oldValue, newValue)
        configChangeHandlers.forEach { it.onModelUpdated(args, ModelChangeTags.NORMAL) }
    }

    val enqueued = mutableListOf<Operation>()
    val flushed = mutableListOf<Operation>()
    val operationRepo: IOperationRepo =
        mockk<IOperationRepo>().also {
            every { it.enqueue(any(), any()) } answers {
                enqueued += firstArg<Operation>()
                if (secondArg()) flushed += firstArg<Operation>()
            }
            coEvery { it.enqueueAndWait(capture(enqueued), any()) } returns true
            every { it.hasSessionCreate(any()) } answers {
                enqueued.any { op -> (op as? CreateSessionOperation)?.localSessionId == firstArg<String>() }
            }
        }

    fun sessionModelStore(action: ((SessionModel) -> Unit)? = null): SessionModelStore {
        if (action != null) action(mockSessionModelStore.model)
        return mockSessionModelStore
    }

    var entryState = AppEntryAction.APP_OPEN
    var isInForeground = true
    val applicationService: IApplicationService =
        MockHelper.applicationService().also {
            every { it.entryState } answers { entryState }
            every { it.isInForeground } answers { isInForeground }
        }

    val sessionService =
        SessionService(
            applicationService,
            configModelStore,
            mockSessionModelStore,
            time,
            featureManager,
            identityModelStore,
            operationRepo,
        ).also { it.launchHeartbeat = ::launchInHeartbeatScope }

    var heartbeatJob: Job? = null

    fun launchInHeartbeatScope(block: suspend () -> Unit): Job = heartbeatScope.launch { block() }.also { heartbeatJob = it }

    private val durationUpdates get() = enqueued.filterIsInstance<UpdateSessionOperation>().filter { !it.isEnd }
    val heartbeats get() = durationUpdates.filter { it !in flushed }
    val checkpoints get() = durationUpdates.filter { it in flushed }

    fun advanceTime(ms: Long) {
        heartbeatScope.advanceTimeBy(ms)
        heartbeatScope.runCurrent()
    }

    val spyCallback = spyk<ISessionLifecycleHandler>()

    companion object {
        const val ONESIGNAL_ID = "onesignal-id"
        const val SUBSCRIPTION_ID = "subscription-id"
        const val EXTERNAL_ID = "external-id"
    }
}

private const val MINUTE_MS = 60_000L
private const val HEARTBEAT_MS = 30 * MINUTE_MS

class SessionServiceTests : FunSpec({
    // SessionService.onFocus/onUnfocused dispatch their state mutation through the now-always-async
    // runOnSerialIO. IOMockHelper.beforeSpec stubs runOnSerialIO to run inline so the tests below
    // can assert session state synchronously after the call. The SDK-4508 tests further down
    // re-mock/unmock runOnSerialIO themselves to assert the dispatch contract; they run last, so
    // their finally-unmock does not affect the earlier inline-dispatch tests.
    listener(IOMockHelper)

    beforeEach { Logging.logLevel = LogLevel.NONE }

    test("session created on focus when current session invalid") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService

        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)

        // When
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.isValid shouldBe true
        sessionModelStore.model.startTime shouldBe mocks.currentTime
        sessionModelStore.model.focusTime shouldBe mocks.currentTime
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }
    }

    test("session created in start when application is in the foreground") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService
        val sessionModelStore = mocks.sessionModelStore()

        // When
        sessionService.bootstrap()
        sessionService.start()
        sessionService.onFocus(true)
        sessionService.subscribe(mocks.spyCallback)

        // Then
        sessionModelStore.model.isValid shouldBe true
        sessionModelStore.model.startTime shouldBe mocks.currentTime
        sessionModelStore.model.focusTime shouldBe mocks.currentTime
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }

        // When
        sessionService.onFocus(false) // Should not trigger a second session

        // Then
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }
    }

    test("session focus time updated when current session valid") {
        // Given
        val startTime = 555L
        val mocks = Mocks()
        val sessionService = mocks.sessionService

        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore =
            mocks.sessionModelStore {
                it.startTime = startTime
                it.isValid = true
            }
        sessionService.subscribe(mocks.spyCallback)

        // When
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.isValid shouldBe true
        sessionModelStore.model.startTime shouldBe mocks.currentTime
        sessionModelStore.model.focusTime shouldBe mocks.currentTime
        verify(exactly = 0) { mocks.spyCallback.onSessionActive() }
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }
    }

    test("session active duration updated when unfocused") {
        // Given
        val startTime = 555L
        val focusTime = 666L
        val startingDuration = 1000L

        val mocks = Mocks()
        val sessionService = mocks.sessionService

        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore =
            mocks.sessionModelStore {
                it.isValid = true
                it.startTime = startTime
                it.focusTime = focusTime
                it.activeDuration = startingDuration
            }

        // When
        sessionService.onUnfocused()

        // Then
        sessionModelStore.model.isValid shouldBe true
        sessionModelStore.model.startTime shouldBe startTime
        sessionModelStore.model.activeDuration shouldBe startingDuration + (mocks.currentTime - focusTime)
    }

    test("session ended when background run") {
        // Given
        val activeDuration = 555L
        val mocks = Mocks()
        val sessionService = mocks.sessionService

        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore =
            mocks.sessionModelStore {
                it.isValid = true
                it.activeDuration = activeDuration
            }
        sessionService.subscribe(mocks.spyCallback)

        // When
        sessionService.backgroundRun()

        // Then
        sessionModelStore.model.isValid shouldBe false
        verify(exactly = 1) { mocks.spyCallback.onSessionEnded(activeDuration, false) }
    }

    test("do not trigger onSessionEnd if session is not active") {
        // Given
        val mocks = Mocks()
        mocks.sessionModelStore { it.isValid = false }
        val sessionService = mocks.sessionService
        sessionService.subscribe(mocks.spyCallback)
        sessionService.bootstrap()
        sessionService.start()

        // When
        sessionService.backgroundRun()

        // Then
        verify(exactly = 0) { mocks.spyCallback.onSessionEnded(any(), any()) }
    }

    test("new session uses the sessions API when the flag is on") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }

        // When
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.usesSessionsApi shouldBe true
    }

    test("new session uses the legacy path when the flag is off") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = false)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }

        // When
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.usesSessionsApi shouldBe false
    }

    test("sessions API choice does not change mid-session") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.sessionsApiEnabled = false
        sessionService.onUnfocused()
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.usesSessionsApi shouldBe true
    }

    test("new session clears the previous session's unfocus time") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore =
            mocks.sessionModelStore {
                it.isValid = false
                it.unfocusTime = 900L
            }

        // When
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.unfocusTime shouldBe null
    }

    test("new session pins onesignal and subscription IDs and a new local session ID") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore =
            mocks.sessionModelStore {
                it.isValid = false
                it.localSessionId = "local-previous"
            }

        // When
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.onesignalId shouldBe Mocks.ONESIGNAL_ID
        sessionModelStore.model.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
        sessionModelStore.model.localSessionId shouldNotBe "local-previous"
        IDManager.isLocalId(sessionModelStore.model.localSessionId) shouldBe true
    }

    test("pinned IDs stay the same after login within a session") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.identityModelStore.model.onesignalId = "new-user-id"
        mocks.configModelStore.model.pushSubscriptionId = "new-subscription-id"
        sessionService.onUnfocused()
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.onesignalId shouldBe Mocks.ONESIGNAL_ID
        sessionModelStore.model.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
    }

    test("session operations keep the app ID the session started with") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.configModelStore.model.appId = "other-app-id"
        mocks.advanceTime(10 * MINUTE_MS)
        sessionService.onUnfocused()
        sessionService.backgroundRun()

        // Then
        mocks.enqueued.filterIsInstance<CreateSessionOperation>().single().appId shouldBe MockHelper.DEFAULT_APP_ID
        val updates = mocks.enqueued.filterIsInstance<UpdateSessionOperation>()
        updates.map { it.isEnd } shouldBe listOf(false, true)
        updates.forEach { it.appId shouldBe MockHelper.DEFAULT_APP_ID }
    }

    test("pinned local IDs are replaced with backend IDs when the user is created") {
        // Given
        val mocks = Mocks()
        mocks.identityModelStore.model.onesignalId = "local-user"
        mocks.configModelStore.model.pushSubscriptionId = "local-subscription"
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.hydrateOnesignalId("backend-user")
        mocks.hydratePushSubscriptionId("backend-subscription")

        // Then
        sessionModelStore.model.onesignalId shouldBe "backend-user"
        sessionModelStore.model.subscriptionId shouldBe "backend-subscription"
    }

    test("pinned subscription ID is not replaced after login before the anonymous user is created") {
        // Given
        val mocks = Mocks()
        mocks.identityModelStore.model.onesignalId = "local-anonymous"
        mocks.configModelStore.model.pushSubscriptionId = "local-subscription"
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        val loggedInModel = IdentityModel().apply { onesignalId = "local-logged-in" }
        every { mocks.identityModelStore.model } returns loggedInModel
        mocks.identityChangeHandler.captured.onModelReplaced(loggedInModel, ModelChangeTags.NORMAL)
        mocks.hydrateOnesignalId("backend-logged-in")
        mocks.hydratePushSubscriptionId("backend-subscription")

        // Then
        sessionModelStore.model.onesignalId shouldBe "local-anonymous"
        sessionModelStore.model.subscriptionId shouldBe "local-subscription"
    }

    test("pinned backend IDs are not replaced by later ID changes") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.hydrateOnesignalId("other-user")
        mocks.hydratePushSubscriptionId("other-subscription")

        // Then
        sessionModelStore.model.onesignalId shouldBe Mocks.ONESIGNAL_ID
        sessionModelStore.model.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
    }

    test("pinned local IDs are not replaced when a different local ID is translated") {
        // Given
        val mocks = Mocks()
        mocks.identityModelStore.model.onesignalId = "local-user"
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.identityModelStore.model.onesignalId = "local-next-user"
        mocks.hydrateOnesignalId("backend-next-user")

        // Then
        sessionModelStore.model.onesignalId shouldBe "local-user"
    }

    test("pinned local IDs are not replaced by another local ID") {
        // Given
        val mocks = Mocks()
        mocks.identityModelStore.model.onesignalId = "local-user"
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.hydrateOnesignalId("local-next-user")

        // Then
        sessionModelStore.model.onesignalId shouldBe "local-user"
    }

    test("new session enqueues a create with the start time and pinned IDs when the flag is on") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.identityModelStore.model.externalId = "external-id"
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }

        // When
        sessionService.onFocus(false)

        // Then
        val create = mocks.enqueued.single() as CreateSessionOperation
        create.appId shouldBe MockHelper.DEFAULT_APP_ID
        create.localSessionId shouldBe sessionModelStore.model.localSessionId
        create.onesignalId shouldBe Mocks.ONESIGNAL_ID
        create.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
        create.startTime shouldBe mocks.currentTime
        create.externalId shouldBe "external-id"
    }

    test("notification open that starts a new session attributes the create to the notification") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }

        // When
        sessionService.attributeNextSessionTo("notification-id")
        mocks.entryState = AppEntryAction.NOTIFICATION_CLICK
        sessionService.onFocus(false)

        // Then
        val create = mocks.enqueued.single() as CreateSessionOperation
        create.directAttributionId shouldBe "notification-id"
    }

    test("notification open during an active session does not change the session or attribute the next one") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        sessionService.onUnfocused()
        val sessionId = sessionModelStore.model.sessionId

        // When
        sessionService.attributeNextSessionTo("notification-id")
        mocks.entryState = AppEntryAction.NOTIFICATION_CLICK
        sessionService.onFocus(false)

        // Then
        sessionModelStore.model.sessionId shouldBe sessionId
        mocks.enqueued.filterIsInstance<CreateSessionOperation>().size shouldBe 1

        // When
        sessionService.onUnfocused()
        sessionService.backgroundRun()
        sessionService.onFocus(false)

        // Then
        val creates = mocks.enqueued.filterIsInstance<CreateSessionOperation>()
        creates.size shouldBe 2
        creates.last().directAttributionId shouldBe null
    }

    test("notification open that does not focus the app is not attributed to a later normal open") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }

        // When
        sessionService.attributeNextSessionTo("notification-id")
        mocks.entryState = AppEntryAction.APP_OPEN
        sessionService.onFocus(false)

        // Then
        val create = mocks.enqueued.single() as CreateSessionOperation
        create.directAttributionId shouldBe null
    }

    test("each new session enqueues its own create with a new idempotency key") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        sessionService.onUnfocused()

        // When
        sessionService.backgroundRun()
        sessionService.onFocus(false)

        // Then
        val creates = mocks.enqueued.filterIsInstance<CreateSessionOperation>()
        creates.size shouldBe 2
        creates.map { it.localSessionId }.distinct().size shouldBe 2
        creates.map { it.idempotencyKey }.distinct().size shouldBe 2
    }

    test("resuming within the session timeout does not enqueue another create") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        sessionService.onUnfocused()
        sessionService.onFocus(false)

        // Then
        mocks.enqueued.size shouldBe 1
    }

    test("cold start with a persisted valid session enqueues a create for the new session") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionModelStore =
            mocks.sessionModelStore {
                it.isValid = true
                it.sessionId = "previous-session"
            }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()

        // When
        sessionService.onFocus(true)

        // Then
        val create = mocks.enqueued.single() as CreateSessionOperation
        create.localSessionId shouldBe sessionModelStore.model.localSessionId
        sessionModelStore.model.sessionId shouldNotBe "previous-session"
    }

    test("anonymous session holds its create on focus") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true, externalId = null)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)

        // When
        sessionService.onFocus(false)

        // Then
        mocks.enqueued shouldBe emptyList()
        sessionModelStore.model.isCreateHeld shouldBe true
        sessionModelStore.model.usesSessionsApi shouldBe true
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }
    }

    test("first unfocus sends the held create before the checkpoint, and later unfocuses send no other create") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true, externalId = null)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        val startTime = mocks.currentTime
        sessionService.attributeNextSessionTo("notification-id")
        mocks.entryState = AppEntryAction.NOTIFICATION_CLICK
        sessionService.onFocus(false)
        mocks.advanceTime(10 * MINUTE_MS)
        mocks.currentTime += 10 * MINUTE_MS

        // When
        sessionService.onUnfocused()

        // Then
        val create = mocks.enqueued[0] as CreateSessionOperation
        create.localSessionId shouldBe sessionModelStore.model.localSessionId
        create.onesignalId shouldBe Mocks.ONESIGNAL_ID
        create.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
        create.startTime shouldBe startTime
        create.externalId shouldBe null
        create.directAttributionId shouldBe "notification-id"
        mocks.enqueued[1] shouldBe mocks.checkpoints.single()
        sessionModelStore.model.isCreateHeld shouldBe false
        sessionModelStore.model.isCreateUnconfirmed shouldBe true

        // When
        sessionService.onFocus(false)
        mocks.advanceTime(5 * MINUTE_MS)
        sessionService.onUnfocused()

        // Then
        mocks.enqueued.filterIsInstance<CreateSessionOperation>().size shouldBe 1
        mocks.checkpoints.size shouldBe 2
    }

    test("heartbeat sends the held create before the heartbeat") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true, externalId = null)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.advanceTime(HEARTBEAT_MS)

        // Then
        (mocks.enqueued[0] as CreateSessionOperation).externalId shouldBe null
        mocks.enqueued[1] shouldBe mocks.heartbeats.single()
    }

    test("background run sends the held create before the end") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true, externalId = null)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        sessionService.backgroundRun()

        // Then
        mocks.enqueued[0].shouldBeInstanceOf<CreateSessionOperation>()
        (mocks.enqueued[1] as UpdateSessionOperation).isEnd shouldBe true
    }

    test("cold start after a kill while the create is held sends the create and the end") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true, externalId = null)
        mocks.sessionModelStore {
            it.isValid = true
            it.sessionId = "previous-session"
            it.localSessionId = "local-previous-session"
            it.usesSessionsApi = true
            it.isCreateHeld = true
            it.directAttributionId = "notification-id"
            it.startTime = 100L
            it.onesignalId = Mocks.ONESIGNAL_ID
            it.subscriptionId = Mocks.SUBSCRIPTION_ID
            it.appId = MockHelper.DEFAULT_APP_ID
            it.focusTime = 500L
        }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()

        // When
        sessionService.onFocus(false)

        // Then
        val create = mocks.enqueued[0] as CreateSessionOperation
        create.localSessionId shouldBe "local-previous-session"
        create.startTime shouldBe 100L
        create.directAttributionId shouldBe "notification-id"
        val end = mocks.enqueued[1] as UpdateSessionOperation
        end.sessionId shouldBe "local-previous-session"
        end.endTime shouldBe 500L
    }

    test("cold start resends a released create the queue never stored, then ends the session") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true, externalId = null)
        mocks.sessionModelStore {
            it.isValid = true
            it.sessionId = "previous-session"
            it.localSessionId = "local-previous-session"
            it.usesSessionsApi = true
            it.isCreateUnconfirmed = true
            it.directAttributionId = "notification-id"
            it.startTime = 100L
            it.onesignalId = Mocks.ONESIGNAL_ID
            it.subscriptionId = Mocks.SUBSCRIPTION_ID
            it.appId = MockHelper.DEFAULT_APP_ID
            it.focusTime = 500L
        }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()

        // When
        sessionService.onFocus(false)

        // Then
        val create = mocks.enqueued[0].shouldBeInstanceOf<CreateSessionOperation>()
        create.localSessionId shouldBe "local-previous-session"
        create.startTime shouldBe 100L
        create.directAttributionId shouldBe "notification-id"
        mocks.enqueued[1].shouldBeInstanceOf<UpdateSessionOperation>().sessionId shouldBe "local-previous-session"
    }

    test("cold start sends no second create when the queue has the released one") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true, externalId = null)
        every { mocks.operationRepo.hasSessionCreate("local-previous-session") } returns true
        mocks.sessionModelStore {
            it.isValid = true
            it.localSessionId = "local-previous-session"
            it.usesSessionsApi = true
            it.isCreateUnconfirmed = true
            it.onesignalId = Mocks.ONESIGNAL_ID
            it.subscriptionId = Mocks.SUBSCRIPTION_ID
            it.appId = MockHelper.DEFAULT_APP_ID
            it.focusTime = 500L
        }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()

        // When
        sessionService.onFocus(false)

        // Then
        mocks.enqueued[0].shouldBeInstanceOf<UpdateSessionOperation>().sessionId shouldBe "local-previous-session"
        mocks.enqueued.filterIsInstance<CreateSessionOperation>().map { it.localSessionId } shouldNotContain "local-previous-session"
    }

    test("anonymous session under identity verification keeps its create held while its user is local") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true, externalId = null)
        mocks.identityModelStore.model.onesignalId = "local-user"
        mocks.configModelStore.model.useIdentityVerification = JwtRequirement.REQUIRED
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(HEARTBEAT_MS)
        sessionService.onUnfocused()

        // When
        sessionService.backgroundRun()

        // Then
        mocks.enqueued shouldBe emptyList()
        sessionModelStore.model.isValid shouldBe false
        sessionModelStore.model.isCreateHeld shouldBe false
    }

    test("anonymous session under identity verification with backend IDs sends its create on the first unfocus") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true, externalId = null)
        mocks.configModelStore.model.useIdentityVerification = JwtRequirement.REQUIRED
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.enqueued shouldBe emptyList()

        mocks.advanceTime(10 * MINUTE_MS)

        // When
        sessionService.onUnfocused()

        // Then
        val create = mocks.enqueued[0].shouldBeInstanceOf<CreateSessionOperation>()
        create.onesignalId shouldBe Mocks.ONESIGNAL_ID
        create.externalId shouldBe null
        mocks.enqueued[1] shouldBe mocks.checkpoints.single()
    }

    test("anonymous session with an unknown identity verification requirement holds its local create until the end") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true, externalId = null)
        mocks.identityModelStore.model.onesignalId = "local-user"
        mocks.configModelStore.model.useIdentityVerification = JwtRequirement.UNKNOWN
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(HEARTBEAT_MS)
        sessionService.onUnfocused()
        mocks.enqueued shouldBe emptyList()
        sessionModelStore.model.isCreateHeld shouldBe true

        // When
        sessionService.backgroundRun()

        // Then
        mocks.enqueued[0].shouldBeInstanceOf<CreateSessionOperation>().onesignalId shouldBe "local-user"
        mocks.enqueued[1].shouldBeInstanceOf<UpdateSessionOperation>().endTime shouldNotBe null
        sessionModelStore.model.isCreateHeld shouldBe false
    }

    test("anonymous session with backend IDs sends its create on the first unfocus while the requirement is unknown") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true, externalId = null)
        mocks.configModelStore.model.useIdentityVerification = JwtRequirement.UNKNOWN
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(10 * MINUTE_MS)

        // When
        sessionService.onUnfocused()

        // Then
        mocks.enqueued[0].shouldBeInstanceOf<CreateSessionOperation>().onesignalId shouldBe Mocks.ONESIGNAL_ID
        mocks.enqueued[1] shouldBe mocks.checkpoints.single()
    }

    test("identified session under identity verification sends its create on focus") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.configModelStore.model.useIdentityVerification = JwtRequirement.REQUIRED
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }

        // When
        sessionService.onFocus(false)

        // Then
        (mocks.enqueued.single() as CreateSessionOperation).externalId shouldBe Mocks.EXTERNAL_ID
        sessionModelStore.model.isCreateHeld shouldBe false
    }

    test("held create waits for consent") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true, externalId = null)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(10 * MINUTE_MS)

        // When
        mocks.setConsentGiven(false)
        sessionService.onUnfocused()

        // Then
        mocks.enqueued shouldBe emptyList()
        sessionModelStore.model.isCreateHeld shouldBe true

        // When
        mocks.setConsentGiven(true)
        sessionService.onFocus(false)
        mocks.advanceTime(MINUTE_MS)
        sessionService.onUnfocused()

        // Then
        mocks.enqueued[0].shouldBeInstanceOf<CreateSessionOperation>()
        mocks.checkpoints.single().activeDuration shouldBe 11 * MINUTE_MS
    }

    test("new session does not enqueue a create when the flag is off") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = false)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)

        // When
        sessionService.onFocus(false)

        // Then
        mocks.enqueued shouldBe emptyList()
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }
    }

    test("new session without a push subscription ID falls back to legacy reporting") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.configModelStore.model.pushSubscriptionId = null
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)

        // When
        sessionService.onFocus(false)

        // Then
        mocks.enqueued shouldBe emptyList()
        sessionModelStore.model.usesSessionsApi shouldBe false
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }
    }

    test("session end tells handlers the ended session used the sessions API") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)
        sessionService.onFocus(false)
        sessionService.onUnfocused()

        // When
        sessionService.backgroundRun()

        // Then
        verify(exactly = 1) { mocks.spyCallback.onSessionEnded(any(), true) }
    }

    test("session end does not reset a session that starts while handlers run") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.elapsedRealtime += 750L
        sessionService.onUnfocused()
        // Stands in for a focus that starts the next session as soon as isValid is cleared.
        every { mocks.spyCallback.onSessionEnded(any(), any()) } answers {
            sessionModelStore.model.isValid shouldBe false
            sessionModelStore.model.activeDuration shouldBe 0L
            sessionModelStore.model.activeDuration = 100L
            sessionModelStore.model.lastHeartbeatDuration = 100L
            sessionModelStore.model.lastHeartbeatTime = 5000L
        }
        sessionService.subscribe(mocks.spyCallback)

        // When
        sessionService.backgroundRun()

        // Then
        verify(exactly = 1) { mocks.spyCallback.onSessionEnded(750L, true) }
        sessionModelStore.model.activeDuration shouldBe 100L
        sessionModelStore.model.lastHeartbeatDuration shouldBe 100L
        sessionModelStore.model.lastHeartbeatTime shouldBe 5000L
    }

    test("background run sends the end with the unfocus time and final duration when the flag is on") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.elapsedRealtime += 750L
        mocks.currentTime = 2222L
        sessionService.onUnfocused()
        mocks.currentTime = 32222L

        // When
        sessionService.backgroundRun()

        // Then
        val end = mocks.enqueued.filterIsInstance<UpdateSessionOperation>().single()
        end.appId shouldBe MockHelper.DEFAULT_APP_ID
        end.sessionId shouldBe sessionModelStore.model.localSessionId
        end.onesignalId shouldBe Mocks.ONESIGNAL_ID
        end.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
        end.activeDuration shouldBe 750L
        end.endTime shouldBe 2222L
        coVerify(exactly = 1) { mocks.operationRepo.enqueueAndWait(end, true) }
        sessionModelStore.model.isValid shouldBe false
    }

    test("background run waits for the end on an IO thread rather than the caller's") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        var waitThread: Thread? = null
        coEvery { mocks.operationRepo.enqueueAndWait(any(), any()) } coAnswers {
            waitThread = Thread.currentThread()
            true
        }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        sessionService.onUnfocused()

        // When
        sessionService.backgroundRun()

        // Then
        waitThread shouldNotBe null
        waitThread shouldNotBe Thread.currentThread()
    }

    test("background run returns when the end is not sent in time and leaves it queued") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        coEvery { mocks.operationRepo.enqueueAndWait(any(), any()) } coAnswers { awaitCancellation() }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        sessionService.onUnfocused()

        runTest {
            sessionService.ioDispatcher = StandardTestDispatcher(testScheduler)

            // When
            sessionService.backgroundRun()

            // Then
            testScheduler.currentTime shouldBe 30_000L
        }
        coVerify(exactly = 1) { mocks.operationRepo.enqueueAndWait(ofType<UpdateSessionOperation>(), true) }
        coVerify(exactly = 0) { mocks.operationRepo.enqueue(ofType<UpdateSessionOperation>(), any()) }
        sessionModelStore.model.isValid shouldBe false
    }

    test("cold start ends the previous sessions API session before creating a new one") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.sessionModelStore {
            it.isValid = true
            it.sessionId = "previous-session"
            it.localSessionId = "local-previous-session"
            it.usesSessionsApi = true
            it.onesignalId = Mocks.ONESIGNAL_ID
            it.subscriptionId = Mocks.SUBSCRIPTION_ID
            it.appId = MockHelper.DEFAULT_APP_ID
            it.focusTime = 500L
            it.unfocusTime = 900L
            it.activeDuration = 400L
        }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()

        // When
        sessionService.onFocus(false)

        // Then
        val end = mocks.enqueued[0] as UpdateSessionOperation
        end.sessionId shouldBe "local-previous-session"
        end.activeDuration shouldBe 400L
        end.endTime shouldBe 900L
        (mocks.enqueued[1] is CreateSessionOperation) shouldBe true
        coVerify(exactly = 0) { mocks.operationRepo.enqueueAndWait(any(), any()) }
    }

    test("cold start after a kill while focused ends the previous session at its last focus") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.sessionModelStore {
            it.isValid = true
            it.sessionId = "previous-session"
            it.localSessionId = "local-previous-session"
            it.usesSessionsApi = true
            it.onesignalId = Mocks.ONESIGNAL_ID
            it.subscriptionId = Mocks.SUBSCRIPTION_ID
            it.appId = MockHelper.DEFAULT_APP_ID
            it.unfocusTime = 300L
            it.focusTime = 500L
        }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()

        // When
        sessionService.onFocus(false)

        // Then
        val end = mocks.enqueued.filterIsInstance<UpdateSessionOperation>().single()
        end.endTime shouldBe 500L
    }

    test("session end does not send an end when the session uses the legacy path") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = false)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)
        sessionService.onFocus(false)
        sessionService.onUnfocused()
        mocks.sessionsApiEnabled = true

        // When
        sessionService.backgroundRun()

        // Then
        mocks.enqueued shouldBe emptyList()
        coVerify(exactly = 0) { mocks.operationRepo.enqueueAndWait(any(), any()) }
        verify(exactly = 1) { mocks.spyCallback.onSessionEnded(any(), any()) }
    }

    test("session end without a push subscription ID sends no end and reports a legacy session") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.configModelStore.model.pushSubscriptionId = null
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)
        sessionService.onFocus(false)
        sessionService.onUnfocused()

        // When
        sessionService.backgroundRun()

        // Then
        mocks.enqueued shouldBe emptyList()
        verify(exactly = 1) { mocks.spyCallback.onSessionEnded(any(), false) }
    }

    test("sessions API active duration uses the monotonic clock") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.elapsedRealtime += 750L
        sessionService.onUnfocused()

        // Then
        sessionModelStore.model.activeDuration shouldBe 750L
    }

    test("sessions API active duration excludes background time between focuses") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.elapsedRealtime += 300L
        sessionService.onUnfocused()

        // When
        mocks.elapsedRealtime += 10_000L
        sessionService.onFocus(false)
        mocks.elapsedRealtime += 200L
        sessionService.onUnfocused()

        // Then
        sessionModelStore.model.activeDuration shouldBe 500L
    }

    test("sessions API active duration ignores an interval spanning a reboot") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore =
            mocks.sessionModelStore {
                it.isValid = true
                it.usesSessionsApi = true
                it.focusElapsedRealtime = 10_000L
                it.activeDuration = 200L
            }

        // When
        mocks.elapsedRealtime = 100L
        sessionService.onUnfocused()

        // Then
        sessionModelStore.model.activeDuration shouldBe 200L
    }

    test("heartbeat sends the cumulative duration after 30 minutes in the foreground") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.advanceTime(HEARTBEAT_MS - 1)

        // Then
        mocks.heartbeats shouldBe emptyList()

        // When
        mocks.advanceTime(1)

        // Then
        val heartbeat = mocks.heartbeats.single()
        heartbeat.sessionId shouldBe sessionModelStore.model.localSessionId
        heartbeat.onesignalId shouldBe Mocks.ONESIGNAL_ID
        heartbeat.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
        heartbeat.activeDuration shouldBe HEARTBEAT_MS
        heartbeat.endTime shouldBe null
    }

    test("heartbeat uses the configured interval") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.configModelStore.model.sessionHeartbeatInterval = 5 * MINUTE_MS
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.advanceTime(5 * MINUTE_MS - 1)

        // Then
        mocks.heartbeats shouldBe emptyList()

        // When
        mocks.advanceTime(5 * MINUTE_MS + 1)

        // Then
        mocks.heartbeats.map { it.activeDuration } shouldBe listOf(5 * MINUTE_MS, 10 * MINUTE_MS)
    }

    test("heartbeat falls back to 30 minutes when the stored interval is not positive") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.configModelStore.model.sessionHeartbeatInterval = 0L
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.advanceTime(HEARTBEAT_MS)

        // Then
        mocks.heartbeats.map { it.activeDuration } shouldBe listOf(HEARTBEAT_MS)
    }

    test("heartbeat applies an interval changed mid-session after the current wait") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(MINUTE_MS)

        // When
        mocks.configModelStore.model.sessionHeartbeatInterval = 5 * MINUTE_MS
        mocks.advanceTime(HEARTBEAT_MS + 10 * MINUTE_MS - MINUTE_MS)

        // Then
        mocks.heartbeats.map { it.activeDuration } shouldBe listOf(HEARTBEAT_MS, HEARTBEAT_MS + 5 * MINUTE_MS, HEARTBEAT_MS + 10 * MINUTE_MS)
    }

    test("a params fetch re-arms a running heartbeat with the new interval") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(MINUTE_MS)

        // When
        mocks.configModelStore.model.sessionHeartbeatInterval = 5 * MINUTE_MS
        mocks.replaceConfig()
        mocks.advanceTime(4 * MINUTE_MS - 1)

        // Then
        mocks.heartbeats shouldBe emptyList()

        // When
        mocks.advanceTime(1)

        // Then
        mocks.heartbeats.map { it.activeDuration } shouldBe listOf(5 * MINUTE_MS)
    }

    test("a params fetch does not start a stopped heartbeat") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.isInForeground = false
        sessionService.onUnfocused()

        // When
        mocks.replaceConfig()
        mocks.advanceTime(2 * HEARTBEAT_MS)

        // Then
        mocks.heartbeats shouldBe emptyList()
    }

    test("heartbeat sends again every 30 minutes in the foreground") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.advanceTime(2 * HEARTBEAT_MS)

        // Then
        mocks.heartbeats.map { it.activeDuration } shouldBe listOf(HEARTBEAT_MS, 2 * HEARTBEAT_MS)
    }

    test("heartbeat pauses in the background and is due a full interval of foreground time after the checkpoint") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(20 * MINUTE_MS)
        sessionService.onUnfocused()

        // When
        mocks.advanceTime(2 * HEARTBEAT_MS)

        // Then
        mocks.heartbeats shouldBe emptyList()

        // When
        sessionService.onFocus(false)
        mocks.advanceTime(HEARTBEAT_MS - 1)

        // Then
        mocks.heartbeats shouldBe emptyList()

        // When
        mocks.advanceTime(1)

        // Then
        mocks.heartbeats.single().activeDuration shouldBe 20 * MINUTE_MS + HEARTBEAT_MS
    }

    test("unfocus sends a flushed checkpoint with the cumulative duration and no end time") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(10 * MINUTE_MS)

        // When
        sessionService.onUnfocused()

        // Then
        val checkpoint = mocks.checkpoints.single()
        checkpoint.sessionId shouldBe sessionModelStore.model.localSessionId
        checkpoint.onesignalId shouldBe Mocks.ONESIGNAL_ID
        checkpoint.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
        checkpoint.activeDuration shouldBe 10 * MINUTE_MS
        checkpoint.endTime shouldBe null
        sessionModelStore.model.isValid shouldBe true

        // When
        sessionService.onFocus(false)
        mocks.advanceTime(5 * MINUTE_MS)
        sessionService.onUnfocused()

        // Then
        mocks.checkpoints.map { it.activeDuration } shouldBe listOf(10 * MINUTE_MS, 15 * MINUTE_MS)
        mocks.enqueued.filterIsInstance<CreateSessionOperation>().size shouldBe 1
    }

    test("unfocus sends no checkpoint when the duration has not grown by a whole second") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(HEARTBEAT_MS)
        mocks.heartbeats.single().activeDuration shouldBe HEARTBEAT_MS

        // When
        mocks.advanceTime(999)
        sessionService.onUnfocused()
        sessionService.onFocus(false)
        sessionService.onUnfocused()

        // Then
        mocks.checkpoints shouldBe emptyList()
    }

    test("unfocus sends no checkpoint when the session uses the legacy path") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = false)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.currentTime += 10 * MINUTE_MS

        // When
        sessionService.onUnfocused()

        // Then
        mocks.enqueued shouldBe emptyList()
    }

    test("session killed in the background ends with the checkpoint's duration") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(10 * MINUTE_MS)
        mocks.currentTime += 10 * MINUTE_MS
        val unfocusTime = mocks.currentTime
        sessionService.onUnfocused()
        mocks.currentTime += 60 * MINUTE_MS

        // When
        val restarted =
            SessionService(
                MockHelper.applicationService(),
                mocks.configModelStore,
                mocks.sessionModelStore(),
                mocks.time,
                mocks.featureManager,
                mocks.identityModelStore,
                mocks.operationRepo,
            ).also { it.launchHeartbeat = { _ -> mockk(relaxed = true) } }
        mocks.enqueued.clear()
        restarted.bootstrap()
        restarted.start()
        restarted.onFocus(false)

        // Then
        val end = mocks.enqueued.filterIsInstance<UpdateSessionOperation>().single()
        end.activeDuration shouldBe 10 * MINUTE_MS
        end.endTime shouldBe unfocusTime
    }

    test("new session restarts the heartbeat interval") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(HEARTBEAT_MS)
        sessionService.onUnfocused()
        sessionService.backgroundRun()
        mocks.enqueued.clear()

        // When
        sessionService.onFocus(false)
        mocks.advanceTime(HEARTBEAT_MS)

        // Then
        mocks.heartbeats.single().activeDuration shouldBe HEARTBEAT_MS
    }

    test("cold start after a kill following a heartbeat ends with at least the heartbeat's duration and time") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.currentTime += HEARTBEAT_MS
        mocks.advanceTime(HEARTBEAT_MS)
        val heartbeatTime = mocks.currentTime

        // When
        val restarted =
            SessionService(
                MockHelper.applicationService(),
                mocks.configModelStore,
                mocks.sessionModelStore(),
                mocks.time,
                mocks.featureManager,
                mocks.identityModelStore,
                mocks.operationRepo,
            ).also { it.launchHeartbeat = { _ -> mockk(relaxed = true) } }
        mocks.enqueued.clear()
        restarted.bootstrap()
        restarted.start()
        restarted.onFocus(false)

        // Then
        val end = mocks.enqueued.filterIsInstance<UpdateSessionOperation>().single()
        end.isEnd shouldBe true
        end.activeDuration shouldBe HEARTBEAT_MS
        end.endTime shouldBe heartbeatTime
    }

    test("cold start after a kill following a clock-jumped heartbeat ends at the heartbeat's foreground time") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        val focusTime = mocks.currentTime
        mocks.currentTime += 2 * HEARTBEAT_MS
        mocks.advanceTime(HEARTBEAT_MS)

        // When
        val restarted =
            SessionService(
                MockHelper.applicationService(),
                mocks.configModelStore,
                mocks.sessionModelStore(),
                mocks.time,
                mocks.featureManager,
                mocks.identityModelStore,
                mocks.operationRepo,
            ).also { it.launchHeartbeat = { _ -> mockk(relaxed = true) } }
        mocks.enqueued.clear()
        restarted.bootstrap()
        restarted.start()
        restarted.onFocus(false)

        // Then
        val end = mocks.enqueued.filterIsInstance<UpdateSessionOperation>().single()
        end.activeDuration shouldBe HEARTBEAT_MS
        end.endTime shouldBe focusTime + HEARTBEAT_MS
    }

    test("session that unfocused after a heartbeat ends at its unfocus even if the heartbeat's clock ran ahead") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.currentTime += 2 * HEARTBEAT_MS
        mocks.advanceTime(HEARTBEAT_MS)
        mocks.currentTime -= HEARTBEAT_MS
        val unfocusTime = mocks.currentTime
        sessionService.onUnfocused()

        // When
        sessionService.backgroundRun()

        // Then
        val end = mocks.enqueued.filterIsInstance<UpdateSessionOperation>().single { it.isEnd }
        end.endTime shouldBe unfocusTime
        end.activeDuration shouldBe HEARTBEAT_MS
    }

    test("session end clears the last heartbeat") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionModelStore =
            mocks.sessionModelStore {
                it.isValid = true
                it.lastHeartbeatDuration = HEARTBEAT_MS
                it.lastHeartbeatTime = 999L
            }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()

        // When
        sessionService.backgroundRun()

        // Then
        sessionModelStore.model.lastHeartbeatDuration shouldBe 0L
        sessionModelStore.model.lastHeartbeatTime shouldBe null
    }

    test("heartbeat that fails stops without an uncaught exception and restarts on the next focus") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        every { mocks.operationRepo.enqueue(ofType<UpdateSessionOperation>(), false) } throws RuntimeException("enqueue failed")
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.advanceTime(2 * HEARTBEAT_MS)

        // Then
        verify(exactly = 1) { mocks.operationRepo.enqueue(ofType<UpdateSessionOperation>(), false) }
        val failed = mocks.heartbeatJob!!
        failed.isCompleted shouldBe true
        failed.isCancelled shouldBe false

        // When
        sessionService.onUnfocused()
        sessionService.onFocus(false)
        mocks.advanceTime(HEARTBEAT_MS)

        // Then
        verify(exactly = 2) { mocks.operationRepo.enqueue(ofType<UpdateSessionOperation>(), false) }
    }

    test("heartbeat does not run when the session uses the legacy path") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = false)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }

        // When
        sessionService.onFocus(false)
        mocks.advanceTime(2 * HEARTBEAT_MS)

        // Then
        mocks.enqueued shouldBe emptyList()
    }

    test("heartbeat without a push subscription ID sends nothing and waits for the next interval") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.configModelStore.model.pushSubscriptionId = null
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)

        // When
        mocks.advanceTime(2 * HEARTBEAT_MS)

        // Then
        mocks.enqueued shouldBe emptyList()
    }

    test("session model fields survive a reload from preferences") {
        // Given
        val prefs = MockPreferencesService()
        val store = SessionModelStore(prefs)
        store.model.sessionId = "session-id"
        store.model.startTime = 123L
        store.model.activeDuration = 456L
        store.model.unfocusTime = 321L
        store.model.focusElapsedRealtime = 789L
        store.model.usesSessionsApi = true
        store.model.onesignalId = Mocks.ONESIGNAL_ID
        store.model.subscriptionId = Mocks.SUBSCRIPTION_ID
        store.model.lastHeartbeatDuration = 654L
        store.model.lastHeartbeatTime = 987L

        // When
        val reloaded = SessionModelStore(prefs).model

        // Then
        reloaded.sessionId shouldBe "session-id"
        reloaded.startTime shouldBe 123L
        reloaded.activeDuration shouldBe 456L
        reloaded.unfocusTime shouldBe 321L
        reloaded.focusElapsedRealtime shouldBe 789L
        reloaded.usesSessionsApi shouldBe true
        reloaded.onesignalId shouldBe Mocks.ONESIGNAL_ID
        reloaded.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
        reloaded.lastHeartbeatDuration shouldBe 654L
        reloaded.lastHeartbeatTime shouldBe 987L
    }

    test("revoking consent while focused adds the visit so far and stops the heartbeat") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(10 * MINUTE_MS)

        // When
        mocks.setConsentGiven(false)
        mocks.advanceTime(2 * HEARTBEAT_MS)

        // Then
        sessionModelStore.model.activeDuration shouldBe 10 * MINUTE_MS
        mocks.heartbeats shouldBe emptyList()
    }

    test("time without consent is not counted when consent is given again within the session timeout") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(10 * MINUTE_MS)
        mocks.setConsentGiven(false)
        mocks.currentTime += 10_000L
        mocks.elapsedRealtime += 10_000L

        // When
        mocks.setConsentGiven(true)
        mocks.advanceTime(20 * MINUTE_MS - 1)

        // Then
        mocks.heartbeats shouldBe emptyList()

        // When
        mocks.advanceTime(1)

        // Then
        mocks.heartbeats.single().activeDuration shouldBe HEARTBEAT_MS
    }

    test("consent given after a pause past the session timeout ends the session and starts the next") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        val firstSessionId = sessionModelStore.model.localSessionId
        mocks.advanceTime(10 * MINUTE_MS)
        mocks.setConsentGiven(false)
        mocks.currentTime += 60 * MINUTE_MS

        // When
        mocks.setConsentGiven(true)

        // Then
        val end = mocks.enqueued.filterIsInstance<UpdateSessionOperation>().single { it.isEnd }
        end.sessionId shouldBe firstSessionId
        end.activeDuration shouldBe 10 * MINUTE_MS
        val next = mocks.enqueued.filterIsInstance<CreateSessionOperation>().last()
        next.localSessionId shouldNotBe firstSessionId
        next.startTime shouldBe mocks.currentTime
    }

    test("a session that started without consent starts over when consent is given") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)
        mocks.setConsentGiven(false)
        sessionService.onFocus(false)
        val unreportedSessionId = sessionModelStore.model.localSessionId
        mocks.currentTime += 5_000L

        // When
        mocks.setConsentGiven(true)

        // Then
        val next = mocks.enqueued.filterIsInstance<CreateSessionOperation>().last()
        next.localSessionId shouldNotBe unreportedSessionId
        next.startTime shouldBe mocks.currentTime
        verify(exactly = 2) { mocks.spyCallback.onSessionStarted() }
        verify(exactly = 0) { mocks.spyCallback.onSessionEnded(any(), any()) }
    }

    test("unfocus without consent sends no checkpoint, and background run queues the end without waiting for it") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(10 * MINUTE_MS)
        mocks.setConsentGiven(false)
        mocks.isInForeground = false
        sessionService.onUnfocused()

        // When
        sessionService.backgroundRun()

        // Then
        mocks.enqueued.filterIsInstance<UpdateSessionOperation>().single { it.isEnd }.activeDuration shouldBe 10 * MINUTE_MS
        mocks.checkpoints shouldBe emptyList()
        coVerify(exactly = 0) { mocks.operationRepo.enqueueAndWait(any(), any()) }
    }

    test("focus without consent adds no time and sends no heartbeat") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        mocks.setConsentGiven(false)

        // When
        sessionService.onFocus(false)
        mocks.advanceTime(2 * HEARTBEAT_MS)
        sessionService.onUnfocused()

        // Then
        sessionModelStore.model.activeDuration shouldBe 0L
        mocks.heartbeats shouldBe emptyList()
        mocks.checkpoints shouldBe emptyList()
    }

    test("consent given in the background waits for the next focus") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(10 * MINUTE_MS)
        mocks.setConsentGiven(false)
        mocks.isInForeground = false
        sessionService.onUnfocused()

        // When
        mocks.setConsentGiven(true)
        mocks.advanceTime(2 * HEARTBEAT_MS)

        // Then
        sessionModelStore.model.activeDuration shouldBe 10 * MINUTE_MS
        mocks.heartbeats shouldBe emptyList()
    }

    test("legacy session counts only time with consent") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.currentTime += 10 * MINUTE_MS
        mocks.setConsentGiven(false)
        mocks.currentTime += 10_000L

        // When
        mocks.setConsentGiven(true)
        mocks.currentTime += 20 * MINUTE_MS
        sessionService.onUnfocused()

        // Then
        sessionModelStore.model.activeDuration shouldBe 30 * MINUTE_MS
    }

    test("split while focused ends the session at the switch and starts the next one for the new user") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)
        sessionService.onFocus(false)
        val firstLocalSessionId = sessionModelStore.model.localSessionId
        val firstSessionId = sessionModelStore.model.sessionId
        val appStartTime = sessionModelStore.model.startTime
        mocks.advanceTime(10 * MINUTE_MS)
        mocks.currentTime += 10 * MINUTE_MS
        mocks.enqueued.clear()

        // When
        mocks.switchUser("onesignal-id-b", "external-id-b")
        sessionService.splitSession()

        // Then
        val end = mocks.enqueued[0] as UpdateSessionOperation
        end.sessionId shouldBe firstLocalSessionId
        end.onesignalId shouldBe Mocks.ONESIGNAL_ID
        end.activeDuration shouldBe 10 * MINUTE_MS
        end.endTime shouldBe mocks.currentTime
        val create = mocks.enqueued[1] as CreateSessionOperation
        create.localSessionId shouldBe sessionModelStore.model.localSessionId
        create.localSessionId shouldNotBe firstLocalSessionId
        create.onesignalId shouldBe "onesignal-id-b"
        create.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
        create.externalId shouldBe "external-id-b"
        create.startTime shouldBe mocks.currentTime
        create.directAttributionId shouldBe null
        sessionModelStore.model.sessionId shouldBe firstSessionId
        sessionModelStore.model.startTime shouldBe appStartTime
        sessionModelStore.model.activeDuration shouldBe 0L
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }
        verify(exactly = 0) { mocks.spyCallback.onSessionEnded(any(), any()) }

        // When
        mocks.advanceTime(5 * MINUTE_MS)
        sessionService.onUnfocused()

        // Then
        val checkpoint = mocks.checkpoints.single()
        checkpoint.sessionId shouldBe create.localSessionId
        checkpoint.activeDuration shouldBe 5 * MINUTE_MS
    }

    test("split while focused to an anonymous user holds the next session's create") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.enqueued.clear()

        // When
        mocks.switchUser("onesignal-id-b", null)
        sessionService.splitSession()

        // Then
        (mocks.enqueued.single() as UpdateSessionOperation).isEnd shouldBe true
        sessionModelStore.model.isCreateHeld shouldBe true
        sessionModelStore.model.onesignalId shouldBe "onesignal-id-b"
    }

    test("split while focused restarts the heartbeat for the next session") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(20 * MINUTE_MS)

        // When
        mocks.switchUser("onesignal-id-b", "external-id-b")
        sessionService.splitSession()
        mocks.advanceTime(HEARTBEAT_MS - 1)

        // Then
        mocks.heartbeats shouldBe emptyList()

        // When
        mocks.advanceTime(1)

        // Then
        val heartbeat = mocks.heartbeats.single()
        heartbeat.sessionId shouldBe sessionModelStore.model.localSessionId
        heartbeat.onesignalId shouldBe "onesignal-id-b"
        heartbeat.activeDuration shouldBe HEARTBEAT_MS
    }

    test("split in the background ends the session now and starts the next one on the next focus") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)
        sessionService.onFocus(false)
        val firstLocalSessionId = sessionModelStore.model.localSessionId
        val appStartTime = sessionModelStore.model.startTime
        mocks.advanceTime(10 * MINUTE_MS)
        mocks.currentTime += 10 * MINUTE_MS
        val unfocusTime = mocks.currentTime
        sessionService.onUnfocused()
        mocks.currentTime += 10_000
        mocks.enqueued.clear()

        // When
        mocks.switchUser("onesignal-id-b", "external-id-b")
        sessionService.splitSession()

        // Then
        val end = mocks.enqueued.single() as UpdateSessionOperation
        end.sessionId shouldBe firstLocalSessionId
        end.activeDuration shouldBe 10 * MINUTE_MS
        end.endTime shouldBe unfocusTime
        sessionModelStore.model.isSplitPending shouldBe true
        sessionModelStore.model.isValid shouldBe true

        // When
        mocks.currentTime += 10_000
        sessionService.onFocus(false)

        // Then
        val create = mocks.enqueued[1] as CreateSessionOperation
        create.onesignalId shouldBe "onesignal-id-b"
        create.startTime shouldBe mocks.currentTime
        create.localSessionId shouldNotBe firstLocalSessionId
        sessionModelStore.model.isSplitPending shouldBe false
        sessionModelStore.model.startTime shouldBe appStartTime
        verify(exactly = 1) { mocks.spyCallback.onSessionStarted() }
        verify(exactly = 1) { mocks.spyCallback.onSessionActive() }
        verify(exactly = 0) { mocks.spyCallback.onSessionEnded(any(), any()) }
    }

    test("notification open after a split in the background attributes the next session") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        mocks.advanceTime(10 * MINUTE_MS)
        sessionService.onUnfocused()
        mocks.switchUser("onesignal-id-b", "external-id-b")
        sessionService.splitSession()
        mocks.enqueued.clear()

        // When
        sessionService.attributeNextSessionTo("notification-id")
        mocks.entryState = AppEntryAction.NOTIFICATION_CLICK
        sessionService.onFocus(false)

        // Then
        val create = mocks.enqueued.single().shouldBeInstanceOf<CreateSessionOperation>()
        create.onesignalId shouldBe "onesignal-id-b"
        create.directAttributionId shouldBe "notification-id"
    }

    test("session timing out after a split in the background sends no second end") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.subscribe(mocks.spyCallback)
        sessionService.onFocus(false)
        mocks.advanceTime(10 * MINUTE_MS)
        sessionService.onUnfocused()
        mocks.switchUser("onesignal-id-b", "external-id-b")
        sessionService.splitSession()
        mocks.enqueued.clear()

        // When
        sessionService.backgroundRun()

        // Then
        mocks.enqueued shouldBe emptyList()
        sessionModelStore.model.isValid shouldBe false
        sessionModelStore.model.isSplitPending shouldBe false
        verify(exactly = 1) { mocks.spyCallback.onSessionEnded(any(), true) }

        // When
        sessionService.onFocus(false)

        // Then
        (mocks.enqueued.single() as CreateSessionOperation).onesignalId shouldBe "onesignal-id-b"
        verify(exactly = 2) { mocks.spyCallback.onSessionStarted() }
    }

    test("cold start after a split in the background sends no second end") {
        // Given
        val mocks = Mocks(sessionsApiEnabled = true)
        mocks.sessionModelStore {
            it.isValid = true
            it.usesSessionsApi = true
            it.isSplitPending = true
            it.localSessionId = "local-previous-session"
            it.onesignalId = Mocks.ONESIGNAL_ID
            it.subscriptionId = Mocks.SUBSCRIPTION_ID
        }
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()

        // When
        sessionService.onFocus(false)

        // Then
        val create = mocks.enqueued.single() as CreateSessionOperation
        create.localSessionId shouldNotBe "local-previous-session"
    }

    test("split does nothing for a legacy session") {
        // Given
        val mocks = Mocks()
        val sessionService = mocks.sessionService
        sessionService.bootstrap()
        sessionService.start()
        val sessionModelStore = mocks.sessionModelStore { it.isValid = false }
        sessionService.onFocus(false)
        val sessionId = sessionModelStore.model.sessionId

        // When
        mocks.switchUser("onesignal-id-b", "external-id-b")
        sessionService.splitSession()

        // Then
        mocks.enqueued shouldBe emptyList()
        sessionModelStore.model.sessionId shouldBe sessionId
        sessionModelStore.model.isSplitPending shouldBe false
    }

    test("onFocus dispatches the session-mutation body through runOnSerialIO (SDK-4508)") {
        // SDK-4508: SessionService.onFocus runs on the main thread via
        // ApplicationService.handleFocus -> applicationLifecycleNotifier.fire. Its body fires
        // session lifecycle handlers (operation repo, IAM trigger eval, etc.) which can in turn
        // touch OneSignalDispatchers' cold-init chain. The fix wraps the body in
        // runOnSerialIO; this test pins down the dispatch contract.
        //
        // Stub the helper as a pass-through so the underlying state mutations still happen
        // (`startTime`, `focusTime`, lifecycle-handler fires) and the existing assertions
        // about session state remain meaningful.
        val threadUtilsPath = "com.onesignal.common.threading.ThreadUtilsKt"
        mockkStatic(threadUtilsPath)
        mockkObject(OneSignalDispatchers)
        every { runOnSerialIO(any<() -> Unit>()) } answers {
            firstArg<() -> Unit>().invoke()
        }
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        try {
            val mocks = Mocks()
            val sessionService = mocks.sessionService
            sessionService.bootstrap()
            sessionService.start()
            mocks.sessionModelStore { it.isValid = false }

            sessionService.onFocus(firedOnSubscribe = false)

            verify(exactly = 1) { runOnSerialIO(any<() -> Unit>()) }
        } finally {
            unmockkObject(OneSignalDispatchers)
            unmockkStatic(threadUtilsPath)
        }
    }

    test("onUnfocused dispatches the activeDuration update through runOnSerialIO (SDK-4508)") {
        val threadUtilsPath = "com.onesignal.common.threading.ThreadUtilsKt"
        mockkStatic(threadUtilsPath)
        mockkObject(OneSignalDispatchers)
        every { runOnSerialIO(any<() -> Unit>()) } answers {
            firstArg<() -> Unit>().invoke()
        }
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        try {
            val mocks = Mocks()
            val sessionService = mocks.sessionService
            sessionService.bootstrap()
            sessionService.start()
            mocks.sessionModelStore {
                it.isValid = true
                it.focusTime = 0L
            }

            sessionService.onUnfocused()

            verify(exactly = 1) { runOnSerialIO(any<() -> Unit>()) }
        } finally {
            unmockkObject(OneSignalDispatchers)
            unmockkStatic(threadUtilsPath)
        }
    }

    test("rapid onUnfocused -> onFocus burst dispatches each event through the serial IO helper in submission order (SDK-4508)") {
        // Mirrors the SDK-4505 BackgroundManager burst test. Real-world scenario: the user
        // backgrounds then immediately re-foregrounds the app on the main thread. Both lifecycle
        // events must route through the same serial IO helper in submission order so the serial IO
        // worker sees focusTime / activeDuration mutations in main-thread arrival order. If they
        // ever raced across the IO pool, activeDuration accounting could drift.
        val threadUtilsPath = "com.onesignal.common.threading.ThreadUtilsKt"
        mockkStatic(threadUtilsPath)
        mockkObject(OneSignalDispatchers)
        every { runOnSerialIO(any<() -> Unit>()) } just runs
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        try {
            val mocks = Mocks()
            val sessionService = mocks.sessionService
            sessionService.bootstrap()
            sessionService.start()
            mocks.sessionModelStore { it.isValid = true }

            sessionService.onUnfocused()
            sessionService.onFocus(firedOnSubscribe = false)

            verify(exactly = 2) { runOnSerialIO(any<() -> Unit>()) }
            verifyOrder {
                runOnSerialIO(any<() -> Unit>())
                runOnSerialIO(any<() -> Unit>())
            }
        } finally {
            unmockkObject(OneSignalDispatchers)
            unmockkStatic(threadUtilsPath)
        }
    }
})
