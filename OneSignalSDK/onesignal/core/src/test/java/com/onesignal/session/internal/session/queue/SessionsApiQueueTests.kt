package com.onesignal.session.internal.session.queue

import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.session.internal.session.backend.CreateSessionRequest
import com.onesignal.session.internal.session.backend.ISessionsBackendService
import com.onesignal.session.internal.session.backend.UpdateSessionRequest
import com.onesignal.session.internal.session.queue.impl.SessionsApiQueue
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot

private class Mocks {
    val prefs = MockPreferencesService()
    val backend = mockk<ISessionsBackendService>()
    var elapsedRealtime = 1_000_000L
    val time = mockk<ITime>().also { every { it.elapsedRealtimeMillis } answers { elapsedRealtime } }
    val deviceService = mockk<IDeviceService>().also { every { it.deviceType } returns IDeviceService.DeviceType.Android }
    val identityModelStore = MockHelper.identityModelStore { it.onesignalId = ONESIGNAL_ID }
    val configModelStore = MockHelper.configModelStore { it.pushSubscriptionId = SUBSCRIPTION_ID }
    val applicationService =
        MockHelper.applicationService().also {
            every { it.appContext } throws IllegalStateException("no context in unit tests")
            every { it.isInForeground } returns true
        }
    val identityChangeHandler = slot<ISingletonModelStoreChangeHandler<IdentityModel>>()

    val createKeys = mutableListOf<String>()
    val updates = mutableListOf<UpdateSessionRequest>()

    init {
        every { identityModelStore.subscribe(capture(identityChangeHandler)) } just runs
        coEvery { backend.createSession(any(), any()) } answers {
            createKeys.add(secondArg<CreateSessionRequest>().idempotencyKey)
            SERVER_SESSION_ID
        }
        coEvery { backend.updateSession(any(), any(), any()) } answers { updates.add(thirdArg()) }
    }

    fun queue(store: SessionRecordStore = SessionRecordStore(prefs)) =
        SessionsApiQueue(store, backend, deviceService, identityModelStore, configModelStore, applicationService, time)
            .also { it.backoff.jitter = { 1.0 } }

    fun SessionsApiQueue.enqueueSession(
        sessionId: String = SESSION_ID,
        onesignalId: String = ONESIGNAL_ID,
        subscriptionId: String = SUBSCRIPTION_ID,
    ) = enqueueCreate(sessionId, APP_ID, onesignalId, subscriptionId, startTimeMillis = 1_700_000_000_000L)

    fun createFailsWith(vararg statusCodes: Int) {
        val responses = statusCodes.toMutableList()
        coEvery { backend.createSession(any(), any()) } answers {
            createKeys.add(secondArg<CreateSessionRequest>().idempotencyKey)
            responses.removeFirstOrNull()?.let { throw BackendException(it) }
            SERVER_SESSION_ID
        }
    }

    fun hydrateOnesignalId(backendId: String) {
        val model = identityModelStore.model
        val localId = model.onesignalId
        model.onesignalId = backendId
        identityChangeHandler.captured.onModelUpdated(
            ModelChangedArgs(model, IdentityConstants.ONESIGNAL_ID, IdentityConstants.ONESIGNAL_ID, localId, backendId),
            ModelChangeTags.HYDRATE,
        )
    }

    companion object {
        const val APP_ID = "app-id"
        const val SESSION_ID = "session-id"
        const val ONESIGNAL_ID = "onesignal-id"
        const val SUBSCRIPTION_ID = "subscription-id"
        const val SERVER_SESSION_ID = "server-session-id"
    }
}

class SessionsApiQueueTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("sends the create, then the end, and forgets the session") {
        // Given
        val mocks = Mocks()
        val store = SessionRecordStore(mocks.prefs)
        val queue = with(mocks) { queue(store).apply { enqueueSession() } }
        queue.enqueueUpdate(Mocks.SESSION_ID, durationMillis = 42_500L, endTimeMillis = 1_700_000_060_000L)

        // When
        queue.flush()

        // Then
        coVerifyOrder {
            mocks.backend.createSession(
                Mocks.APP_ID,
                withArg {
                    it.onesignalId shouldBe Mocks.ONESIGNAL_ID
                    it.subscriptionId shouldBe Mocks.SUBSCRIPTION_ID
                    it.deviceType shouldBe 1
                    it.startTime shouldBe 1_700_000_000L
                },
            )
            mocks.backend.updateSession(
                Mocks.APP_ID,
                Mocks.SERVER_SESSION_ID,
                withArg {
                    it.durationSeconds shouldBe 42L
                    it.endTime shouldBe 1_700_000_060L
                },
            )
        }
        store.list() shouldHaveSize 0
    }

    test("a newer update replaces an unsent one") {
        // Given
        val mocks = Mocks()
        val queue = with(mocks) { queue().apply { enqueueSession() } }
        queue.enqueueUpdate(Mocks.SESSION_ID, durationMillis = 10_000L)

        // When
        queue.enqueueUpdate(Mocks.SESSION_ID, durationMillis = 20_000L)
        queue.flush()

        // Then
        mocks.updates.map { it.durationSeconds } shouldBe listOf(20L)
    }

    test("an update after the end is ignored") {
        // Given
        val mocks = Mocks()
        val queue = with(mocks) { queue().apply { enqueueSession() } }
        queue.enqueueUpdate(Mocks.SESSION_ID, durationMillis = 10_000L, endTimeMillis = 1_700_000_060_000L)

        // When
        queue.enqueueUpdate(Mocks.SESSION_ID, durationMillis = 20_000L)
        queue.flush()

        // Then
        mocks.updates.map { it.durationSeconds to it.endTime } shouldBe listOf(10L to 1_700_000_060L)
    }

    test("a sent duration update keeps the session for later updates") {
        // Given
        val mocks = Mocks()
        val queue = with(mocks) { queue().apply { enqueueSession() } }
        queue.enqueueUpdate(Mocks.SESSION_ID, durationMillis = 10_000L)
        queue.flush()

        // When
        queue.enqueueUpdate(Mocks.SESSION_ID, durationMillis = 20_000L, endTimeMillis = 1_700_000_060_000L)
        queue.flush()

        // Then
        mocks.updates.map { it.durationSeconds } shouldBe listOf(10L, 20L)
        coVerify(exactly = 1) { mocks.backend.createSession(any(), any()) }
    }

    test("a retry reuses the idempotency key, including after a restart") {
        // Given
        val mocks = Mocks()
        mocks.createFailsWith(500)
        with(mocks) { queue().apply { enqueueSession() } }.flush()

        // When
        mocks.queue().flush()

        // Then
        mocks.createKeys shouldHaveSize 2
        mocks.createKeys.distinct() shouldHaveSize 1
    }

    test("a create that succeeds without a session ID is retried") {
        // Given
        val mocks = Mocks()
        val serverIds = mutableListOf(null, Mocks.SERVER_SESSION_ID)
        coEvery { mocks.backend.createSession(any(), any()) } answers { serverIds.removeFirst() }
        val queue = with(mocks) { queue().apply { enqueueSession() } }
        queue.enqueueUpdate(Mocks.SESSION_ID, durationMillis = 10_000L)

        // When
        queue.flush()
        queue.flush()

        // Then
        coVerify(exactly = 2) { mocks.backend.createSession(any(), any()) }
        mocks.updates shouldHaveSize 1
    }

    test("a create rejected with a 4xx is dropped with its updates") {
        // Given
        val mocks = Mocks()
        mocks.createFailsWith(422)
        val store = SessionRecordStore(mocks.prefs)
        val queue = with(mocks) { queue(store).apply { enqueueSession() } }
        queue.enqueueUpdate(Mocks.SESSION_ID, durationMillis = 10_000L)

        // When
        queue.flush()

        // Then
        store.list() shouldHaveSize 0
        mocks.updates shouldHaveSize 0
    }

    test("a request is dropped after 5 failures the backend answered") {
        // Given
        val mocks = Mocks()
        mocks.createFailsWith(500, 500, 500, 500, 500, 500)
        val store = SessionRecordStore(mocks.prefs)
        val queue = with(mocks) { queue(store).apply { enqueueSession() } }

        // When
        repeat(5) { queue.flush() }

        // Then
        store.list() shouldHaveSize 0
    }

    test("network errors don't count toward the attempt cap") {
        // Given
        val mocks = Mocks()
        mocks.createFailsWith(0, 0, 0, 0, 0, 0)
        val store = SessionRecordStore(mocks.prefs)
        val queue = with(mocks) { queue(store).apply { enqueueSession() } }

        // When
        repeat(6) { queue.flush() }
        queue.flush()

        // Then
        mocks.createKeys shouldHaveSize 7
        store.get(Mocks.SESSION_ID)?.serverSessionId shouldBe Mocks.SERVER_SESSION_ID
    }

    test("a failing session doesn't block other sessions after it is dropped") {
        // Given
        val mocks = Mocks()
        mocks.createFailsWith(422)
        val queue =
            with(mocks) {
                queue().apply {
                    enqueueSession(sessionId = "first")
                    enqueueSession(sessionId = "second")
                }
            }

        // When
        queue.flush()

        // Then
        mocks.createKeys shouldHaveSize 2
    }

    test("backoff doubles after each failure, up to the cap") {
        // Given
        val mocks = Mocks()
        mocks.createFailsWith(*IntArray(9) { 0 })
        val queue = with(mocks) { queue().apply { enqueueSession() } }

        // When
        val waits =
            (1..9).map {
                queue.flush()
                queue.backoff.waitMillis
            }

        // Then
        waits shouldBe listOf(5_000L, 10_000L, 20_000L, 40_000L, 80_000L, 160_000L, 300_000L, 300_000L, 300_000L)
    }

    test("Retry-After holds requests even when flushed") {
        // Given
        val mocks = Mocks()
        coEvery { mocks.backend.createSession(any(), any()) } throws BackendException(429, retryAfterSeconds = 60)
        val queue = with(mocks) { queue().apply { enqueueSession() } }
        queue.flush()

        // When
        queue.retryNow()
        queue.flush()

        // Then
        queue.backoff.waitMillis shouldBe 60_000L
        coVerify(exactly = 1) { mocks.backend.createSession(any(), any()) }
    }

    test("retryNow ends the failure backoff") {
        // Given
        val mocks = Mocks()
        mocks.createFailsWith(500)
        val queue = with(mocks) { queue().apply { enqueueSession() } }
        queue.flush()

        // When
        queue.retryNow()

        // Then
        queue.backoff.waitMillis shouldBe 0L
    }

    test("the oldest session is dropped when the queue is full") {
        // Given
        val mocks = Mocks()
        val store = SessionRecordStore(mocks.prefs)
        val queue = mocks.queue(store)

        // When
        with(mocks) { repeat(101) { queue.enqueueSession(sessionId = "session-$it") } }

        // Then
        store.list() shouldHaveSize 100
        store.get("session-0") shouldBe null
    }

    test("a session waits for its user to be created, then sends with the backend ID") {
        // Given
        val mocks = Mocks()
        mocks.identityModelStore.model.onesignalId = "local-user"
        val queue = with(mocks) { queue().apply { enqueueSession(onesignalId = "local-user") } }
        queue.start()
        queue.flush()
        coVerify(exactly = 0) { mocks.backend.createSession(any(), any()) }

        // When
        mocks.hydrateOnesignalId("backend-user")
        queue.flush()

        // Then
        coVerify(exactly = 1) { mocks.backend.createSession(any(), withArg { it.onesignalId shouldBe "backend-user" }) }
    }

    test("a session whose local user ID is no longer current is dropped") {
        // Given
        val mocks = Mocks()
        val store = SessionRecordStore(mocks.prefs)
        val queue = with(mocks) { queue(store).apply { enqueueSession(onesignalId = "local-previous-user") } }

        // When
        queue.flush()

        // Then
        store.list() shouldHaveSize 0
        coVerify(exactly = 0) { mocks.backend.createSession(any(), any()) }
    }

    test("a session only waits on its own subscription ID") {
        // Given
        val mocks = Mocks()
        mocks.configModelStore.model.pushSubscriptionId = "local-subscription"
        val store = SessionRecordStore(mocks.prefs)
        val queue = with(mocks) { queue(store).apply { enqueueSession(subscriptionId = "local-subscription") } }

        // When
        queue.flush()

        // Then
        store.list() shouldHaveSize 1
        coVerify(exactly = 0) { mocks.backend.createSession(any(), any()) }
    }
})
