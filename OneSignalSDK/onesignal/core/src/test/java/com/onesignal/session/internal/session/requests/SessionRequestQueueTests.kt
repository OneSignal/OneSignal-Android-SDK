package com.onesignal.session.internal.session.requests

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.session.internal.session.backend.CreateSessionRequest
import com.onesignal.session.internal.session.backend.ISessionsBackendService
import com.onesignal.session.internal.session.backend.UpdateSessionRequest
import com.onesignal.session.internal.session.requests.impl.SessionRequestQueue
import com.onesignal.user.internal.operations.impl.states.NewRecordsState
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private const val APP_ID = MockHelper.DEFAULT_APP_ID
private const val ONESIGNAL_ID = "onesignal-id"
private const val SUBSCRIPTION_ID = "subscription-id"

private class Mocks(
    val prefs: MockPreferencesService = MockPreferencesService(),
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    var timeOffset = 0L
    val time: ITime =
        mockk<ITime>().also {
            every { it.currentTimeMillis } answers { System.currentTimeMillis() + timeOffset }
        }

    val newRecordsState = NewRecordsState(time, MockHelper.configModelStore())
    val sessionModelStore = MockHelper.sessionModelStore { it.sessionId = "session-1" }
    val store = SessionRequestStore(prefs)

    val creates = mutableListOf<CreateSessionRequest>()
    val updates = mutableListOf<Pair<String, UpdateSessionRequest>>()
    val backend: ISessionsBackendService =
        mockk<ISessionsBackendService>().also {
            coEvery { it.createSession(APP_ID, any()) } answers {
                val request = secondArg<CreateSessionRequest>()
                creates.add(request)
                "server-${request.idempotencyKey}"
            }
            coEvery { it.updateSession(APP_ID, any(), any()) } answers {
                updates.add(secondArg<String>() to thirdArg())
            }
        }

    val queue =
        SessionRequestQueue(
            store,
            backend,
            MockHelper.deviceService(),
            newRecordsState,
            MockHelper.applicationService(),
            sessionModelStore,
            time,
        )

    fun start() = queue.also { scope.launch { it.processForever() } }
}

private fun create(
    session: String = "session-1",
    onesignalId: String = ONESIGNAL_ID,
    subscriptionId: String = SUBSCRIPTION_ID,
) = SessionRequest.create(APP_ID, session, onesignalId, subscriptionId, startTime = 1_000L)

private fun update(
    session: String = "session-1",
    serverSessionId: String? = null,
    activeDuration: Long = 1_000L,
    endTime: Long? = null,
    onesignalId: String = ONESIGNAL_ID,
) = SessionRequest.update(APP_ID, session, serverSessionId, onesignalId, SUBSCRIPTION_ID, activeDuration, endTime)

private fun backendError(
    statusCode: Int,
    retryAfterSeconds: Int? = null,
) = BackendException(statusCode, retryAfterSeconds = retryAfterSeconds)

class SessionRequestQueueTests : FunSpec({
    timeout = 20_000

    // android.util.Log isn't available in unit tests.
    val originalLogLevel = Logging.logLevel
    beforeSpec { Logging.logLevel = LogLevel.NONE }
    afterSpec { Logging.logLevel = originalLogLevel }
    val scopes = mutableListOf<CoroutineScope>()

    fun mocks(prefs: MockPreferencesService = MockPreferencesService()) = Mocks(prefs).also { scopes.add(it.scope) }

    // Each queue's loop runs in its mocks' scope so it can be cancelled after the test.
    afterTest {
        scopes.forEach { it.cancel() }
        scopes.clear()
    }

    test("sends a create and records the server session ID on the current session") {
        val mocks = mocks()
        val queue = mocks.start()
        val request = create()

        queue.enqueueAndWait(request) shouldBe true

        mocks.creates.single().let {
            it.onesignalId shouldBe ONESIGNAL_ID
            it.subscriptionId shouldBe SUBSCRIPTION_ID
            it.startTime shouldBe 1L
            it.idempotencyKey shouldBe request.idempotencyKey
        }
        mocks.store.list() shouldBe emptyList()
        eventually(5.seconds) { mocks.sessionModelStore.model.serverSessionId shouldBe "server-${request.idempotencyKey}" }
    }

    test("an update waits for its session's create, then uses the server session ID") {
        val mocks = mocks()
        val create = create()
        val end = update(endTime = 5_000L)
        mocks.queue.enqueue(create)
        mocks.queue.enqueue(end)

        mocks.start()

        eventually(5.seconds) { mocks.updates shouldHaveSize 1 }
        mocks.updates.single().let { (sessionId, request) ->
            sessionId shouldBe "server-${create.idempotencyKey}"
            request.endTime shouldBe 5L
            request.idempotencyKey shouldBe end.idempotencyKey
        }
    }

    test("an update built after its create succeeded still gets the server session ID") {
        val mocks = mocks()
        val queue = mocks.start()
        val create = create()
        queue.enqueueAndWait(create) shouldBe true

        queue.enqueueAndWait(update(serverSessionId = null)) shouldBe true

        mocks.updates.single().first shouldBe "server-${create.idempotencyKey}"
    }

    test("a create waits for backend user and subscription IDs") {
        val mocks = mocks()
        val queue = mocks.start()
        val sent = mocks.scope.async { queue.enqueueAndWait(create(onesignalId = "local-user", subscriptionId = "local-sub")) }

        delay(200)
        mocks.creates shouldBe emptyList()

        queue.translateOnesignalId("local-user", "backend-user")
        queue.translateSubscriptionId("local-sub", "backend-sub", "backend-user")

        sent.await() shouldBe true
        mocks.creates.single().let {
            it.onesignalId shouldBe "backend-user"
            it.subscriptionId shouldBe "backend-sub"
        }
    }

    test("a subscription ID is only translated for requests pinned to the user it was created for") {
        val mocks = mocks()
        val request = create(onesignalId = "other-user", subscriptionId = "local-sub")
        mocks.queue.enqueue(request)

        mocks.queue.translateSubscriptionId("local-sub", "backend-sub", ONESIGNAL_ID)

        request.subscriptionId shouldBe "local-sub"
    }

    test("unsent updates for a session coalesce into the latest with the highest duration") {
        val mocks = mocks()
        val queue = mocks.queue
        queue.enqueue(create(onesignalId = "local-user"))
        queue.enqueue(update(activeDuration = 3_000L))
        queue.enqueue(update(activeDuration = 2_000L))

        val updates = mocks.store.list().filter { !it.isCreate }
        updates shouldHaveSize 1
        updates.single().activeDuration shouldBe 3_000L
    }

    test("an end replaces unsent updates and later updates for the session are discarded") {
        val mocks = mocks()
        val queue = mocks.queue
        queue.enqueue(create(onesignalId = "local-user"))
        queue.enqueue(update(activeDuration = 1_000L))
        val end = update(activeDuration = 2_000L, endTime = 5_000L)
        queue.enqueue(end)

        queue.enqueueAndWait(update(activeDuration = 9_000L)) shouldBe false

        mocks.store.list().filter { !it.isCreate } shouldBe listOf(end)
    }

    test("updates for other sessions are not coalesced") {
        val mocks = mocks()
        val queue = mocks.queue
        queue.enqueue(update(session = "session-1", serverSessionId = "server-1", onesignalId = "local-user"))
        queue.enqueue(update(session = "session-2", serverSessionId = "server-2", onesignalId = "local-user"))

        mocks.store.list() shouldHaveSize 2
    }

    test("an update for a session that was never created is discarded") {
        val mocks = mocks()

        mocks.start().enqueueAndWait(update(serverSessionId = null)) shouldBe false

        mocks.store.list() shouldBe emptyList()
    }

    test("the oldest request is dropped past the cap, along with its session's updates") {
        val mocks = mocks()
        val queue = mocks.queue
        queue.enqueue(create(session = "oldest", onesignalId = "local-user"))
        queue.enqueue(update(session = "oldest", onesignalId = "local-user"))

        repeat(SessionRequestQueue.MAX_QUEUED_REQUESTS - 1) { queue.enqueue(create(session = "session-$it", onesignalId = "local-user")) }

        val requests = mocks.store.list()
        requests shouldHaveSize SessionRequestQueue.MAX_QUEUED_REQUESTS - 1
        requests.none { it.localSessionId == "oldest" } shouldBe true
    }

    test("a failed request retries with the same idempotency key") {
        val mocks = mocks()
        val keys = mutableListOf<String>()
        var calls = 0
        coEvery { mocks.backend.createSession(APP_ID, any()) } answers {
            keys.add(secondArg<CreateSessionRequest>().idempotencyKey)
            if (++calls == 1) throw backendError(500) else "server-id"
        }
        val queue = mocks.start()
        val request = create()
        val sent = mocks.scope.async { queue.enqueueAndWait(request) }

        eventually(5.seconds) { request.failedAttempts shouldBe 1 }
        queue.onFocus(false)

        sent.await() shouldBe true
        keys shouldBe listOf(request.idempotencyKey, request.idempotencyKey)
    }

    test("a request backing off does not block other sessions") {
        val mocks = mocks()
        coEvery { mocks.backend.createSession(APP_ID, match { it.onesignalId == "failing-user" }) } throws backendError(503)
        val queue = mocks.start()
        val failing = create(session = "failing", onesignalId = "failing-user")
        queue.enqueue(failing)
        eventually(5.seconds) { failing.failedAttempts shouldBe 1 }

        queue.enqueueAndWait(create(session = "healthy")) shouldBe true

        mocks.store.list() shouldBe listOf(failing)
    }

    test("a request is dropped after the maximum failed attempts, along with its session's updates") {
        val mocks = mocks()
        coEvery { mocks.backend.createSession(APP_ID, any()) } throws backendError(500)
        val queue = mocks.start()
        val create = create()
        val createSent = mocks.scope.async { queue.enqueueAndWait(create) }
        eventually(5.seconds) { mocks.store.list() shouldHaveSize 1 }
        val updateSent = mocks.scope.async { queue.enqueueAndWait(update()) }

        repeat(SessionRequestQueue.MAX_FAILED_ATTEMPTS - 1) { attempt ->
            eventually(5.seconds) { create.failedAttempts shouldBe attempt + 1 }
            queue.onFocus(false)
        }

        createSent.await() shouldBe false
        updateSent.await() shouldBe false
        mocks.store.list() shouldBe emptyList()
        coVerify(exactly = SessionRequestQueue.MAX_FAILED_ATTEMPTS) { mocks.backend.createSession(any(), any()) }
    }

    test("a non-retryable client error drops the request") {
        val mocks = mocks()
        coEvery { mocks.backend.createSession(APP_ID, any()) } throws backendError(400)

        mocks.start().enqueueAndWait(create()) shouldBe false

        mocks.store.list() shouldBe emptyList()
    }

    test("a request that never reached the backend keeps retrying without counting an attempt") {
        val mocks = mocks()
        var calls = 0
        coEvery { mocks.backend.createSession(APP_ID, any()) } answers {
            if (++calls <= SessionRequestQueue.MAX_FAILED_ATTEMPTS) throw backendError(0) else "server-id"
        }
        val queue = mocks.start()
        val request = create()
        val sent = mocks.scope.async { queue.enqueueAndWait(request) }

        repeat(SessionRequestQueue.MAX_FAILED_ATTEMPTS) { attempt ->
            eventually(5.seconds) { calls shouldBe attempt + 1 }
            delay(50)
            queue.onFocus(false)
        }

        sent.await() shouldBe true
        request.failedAttempts shouldBe 0
    }

    test("a 404 for a just-created user is retried") {
        val mocks = mocks()
        var calls = 0
        coEvery { mocks.backend.createSession(APP_ID, any()) } answers {
            if (++calls == 1) throw backendError(404) else "server-id"
        }
        val configModelStore = MockHelper.configModelStore { it.opRepoPostCreateRetryUpTo = 60_000L }
        val newRecordsState = NewRecordsState(mocks.time, configModelStore).also { it.add(ONESIGNAL_ID) }
        val queue =
            SessionRequestQueue(
                mocks.store,
                mocks.backend,
                MockHelper.deviceService(),
                newRecordsState,
                MockHelper.applicationService(),
                mocks.sessionModelStore,
                mocks.time,
            ).also { queue -> mocks.scope.launch { queue.processForever() } }
        val request = create()
        val sent = mocks.scope.async { queue.enqueueAndWait(request) }

        eventually(5.seconds) { request.failedAttempts shouldBe 1 }
        queue.onFocus(false)

        sent.await() shouldBe true
    }

    test("a request killed mid-send is replayed on the next launch with the same idempotency key") {
        val prefs = MockPreferencesService()
        val first = mocks(prefs)
        val inFlight = CompletableDeferred<Unit>()
        coEvery { first.backend.createSession(APP_ID, any()) } coAnswers {
            inFlight.complete(Unit)
            awaitCancellation()
        }
        val request = create()
        first.start().enqueue(request)
        inFlight.await()
        first.scope.cancel()

        val relaunched = mocks(prefs)
        relaunched.store.list().single().idempotencyKey shouldBe request.idempotencyKey
        relaunched.start()

        eventually(5.seconds) { relaunched.creates shouldHaveSize 1 }
        relaunched.creates.single().idempotencyKey shouldBe request.idempotencyKey
    }

    test("enqueueing does not wait on a request in flight") {
        val mocks = mocks()
        val inFlight = CompletableDeferred<Unit>()
        coEvery { mocks.backend.createSession(APP_ID, any()) } coAnswers {
            inFlight.complete(Unit)
            awaitCancellation()
        }
        val queue = mocks.start()
        queue.enqueue(create(session = "stuck"))
        inFlight.await()

        queue.enqueue(create(session = "next"))

        mocks.store.list().map { it.localSessionId } shouldBe listOf("stuck", "next")
    }

    test("retry delay backs off exponentially within bounds and honors Retry-After") {
        val random = Random(0)
        repeat(20) {
            SessionRequestQueue.retryDelayMs(1, null, random).let {
                it shouldBeGreaterThanOrEqual 2_500L
                it shouldBeLessThanOrEqual 5_000L
            }
            SessionRequestQueue.retryDelayMs(3, null, random).let {
                it shouldBeGreaterThanOrEqual 10_000L
                it shouldBeLessThanOrEqual 20_000L
            }
            SessionRequestQueue.retryDelayMs(30, null, random) shouldBeLessThanOrEqual 5 * 60_000L
        }
        SessionRequestQueue.retryDelayMs(1, 120, random) shouldBe 120_000L
    }
})
