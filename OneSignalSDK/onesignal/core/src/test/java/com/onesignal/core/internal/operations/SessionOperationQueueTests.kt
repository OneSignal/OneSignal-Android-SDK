package com.onesignal.core.internal.operations

import com.onesignal.core.internal.operations.impl.MAX_QUEUED_SESSION_OPERATIONS
import com.onesignal.core.internal.operations.impl.OperationModelStore
import com.onesignal.core.internal.operations.impl.OperationRepo
import com.onesignal.core.internal.time.impl.Time
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.CoreInternalMocks
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.session.internal.session.SessionModel
import com.onesignal.session.internal.session.operations.CreateSessionOperation
import com.onesignal.session.internal.session.operations.SessionOperation
import com.onesignal.session.internal.session.operations.UpdateSessionOperation
import com.onesignal.session.internal.session.operations.impl.SessionOperationExecutor
import com.onesignal.user.internal.jwt.JwtRequirement
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.internal.operations.ExecutorMocks.Companion.getNewRecordState
import com.onesignal.user.internal.operations.LoginUserFromSubscriptionOperation
import com.onesignal.user.internal.operations.LoginUserOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val APP_ID = "appId"
private const val LOCAL_SESSION_ID = "local-session"

private class SessionQueueMocks(
    ivRequired: Boolean = false,
) {
    val configModelStore =
        MockHelper.configModelStore {
            it.isInitializedWithRemote = true
            it.useIdentityVerification = if (ivRequired) JwtRequirement.REQUIRED else JwtRequirement.NOT_REQUIRED
        }

    val sessionModelStore = MockHelper.sessionModelStore { it.sessionId = "session" }

    val storedOperations = mutableListOf<Operation>()
    private val barriers = ConcurrentHashMap<Operation, CompletableDeferred<Unit>>()
    val operationModelStore: OperationModelStore =
        mockk<OperationModelStore>().also { store ->
            every { store.loadOperations() } just runs
            every { store.list() } answers { storedOperations.toList() }
            every { store.add(any()) } answers {
                val op = firstArg<Operation>()
                val barrier = barriers.remove(op)
                if (barrier != null) barrier.complete(Unit) else storedOperations.add(op)
            }
            every { store.add(any<Int>(), any<Operation>()) } answers { storedOperations.add(firstArg<Int>(), secondArg()) }
            every { store.remove(any()) } answers { storedOperations.removeAll { it.id == firstArg<String>() } }
        }

    val executed = mutableListOf<Operation>()
    val executor: IOperationExecutor =
        mockk<IOperationExecutor>().also {
            every { it.operations } returns listOf(SessionOperationExecutor.CREATE_SESSION, SessionOperationExecutor.UPDATE_SESSION)
        }

    val operationRepo =
        OperationRepo(
            listOf(executor),
            operationModelStore,
            configModelStore,
            Time(),
            getNewRecordState(configModelStore),
            JwtTokenStore(MockPreferencesService()),
            CoreInternalMocks.identityVerificationService(newCodePathsRun = ivRequired, ivBehaviorActive = ivRequired),
            sessionModelStore,
        )

    /** The first execution suspends until [release] completes; [started] completes once it begins. */
    fun holdFirstExecution(
        started: CompletableDeferred<Unit>,
        release: CompletableDeferred<Unit>,
        response: (Operation) -> ExecutionResponse,
    ) {
        coEvery { executor.execute(any()) } coAnswers {
            val op = firstArg<List<Operation>>().single()
            executed.add(op)
            if (started.complete(Unit)) release.await()
            response(op)
        }
    }

    fun respondWith(vararg responses: (Operation) -> ExecutionResponse) {
        var call = 0
        coEvery { executor.execute(any()) } answers {
            val op = firstArg<List<Operation>>().single()
            executed.add(op)
            responses[minOf(call++, responses.size - 1)](op)
        }
    }

    /**
     * Enqueues [ops], then waits until the repo's thread has processed them by enqueueing a
     * barrier operation behind them.
     */
    suspend fun enqueueAll(vararg ops: Operation) {
        ops.forEach { operationRepo.enqueue(it) }
        val barrier = mockk<Operation>(relaxed = true)
        every { barrier.id } returns UUID.randomUUID().toString()
        val processed = CompletableDeferred<Unit>()
        barriers[barrier] = processed
        operationRepo.enqueue(barrier)
        withTimeout(2_000) { processed.await() }
    }

    val queuedOperations: List<Operation>
        get() =
            synchronized(operationRepo.queue) {
                operationRepo.queue.map { it.operation }.filter { it is CreateSessionOperation || it is UpdateSessionOperation }
            }
}

private fun create(
    localSessionId: String = LOCAL_SESSION_ID,
    onesignalId: String = "onesignal-id",
) = CreateSessionOperation(APP_ID, localSessionId, onesignalId, "subscription-id", startTime = 1_000L)

private fun update(
    activeDuration: Long,
    sessionId: String = LOCAL_SESSION_ID,
    endTime: Long? = null,
) = UpdateSessionOperation(APP_ID, sessionId, "onesignal-id", "subscription-id", activeDuration, endTime)

class SessionOperationQueueTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("queued duration-only updates collapse into the newest update with the highest duration") {
        // Given
        val mocks = SessionQueueMocks()
        val first = update(activeDuration = 20_000)
        val second = update(activeDuration = 10_000)

        // When
        mocks.enqueueAll(first, second)

        // Then
        mocks.queuedOperations shouldContainExactly listOf(second)
        second.activeDuration shouldBe 20_000
        mocks.storedOperations shouldContainExactly listOf(second)
    }

    test("coalescing keeps the highest failed attempt count") {
        // Given
        val mocks = SessionQueueMocks()
        val retried = update(activeDuration = 10_000).apply { failedAttempts = 3 }
        val next = update(activeDuration = 20_000)

        // When
        mocks.enqueueAll(retried, next)

        // Then
        mocks.queuedOperations shouldContainExactly listOf(next)
        next.failedAttempts shouldBe 3
    }

    test("an end replaces an unsent update for the same session") {
        // Given
        val mocks = SessionQueueMocks()
        val duration = update(activeDuration = 10_000)
        val end = update(activeDuration = 15_000, endTime = 5_000L)

        // When
        mocks.enqueueAll(duration, end)

        // Then
        mocks.queuedOperations shouldContainExactly listOf(end)
        end.activeDuration shouldBe 15_000
        end.isEnd shouldBe true
    }

    test("an update for a session whose end is already queued is discarded") {
        // Given
        val mocks = SessionQueueMocks()
        val end = update(activeDuration = 15_000, endTime = 5_000L)
        val late = update(activeDuration = 20_000)

        // When
        mocks.enqueueAll(end, late)

        // Then
        mocks.queuedOperations shouldContainExactly listOf(end)
        mocks.storedOperations shouldContainExactly listOf(end)
    }

    test("updates for different sessions are not coalesced") {
        // Given
        val mocks = SessionQueueMocks()
        val a = update(activeDuration = 10_000, sessionId = "local-a")
        val b = update(activeDuration = 10_000, sessionId = "local-b")

        // When
        mocks.enqueueAll(a, b)

        // Then
        mocks.queuedOperations shouldContainExactly listOf(a, b)
    }

    test("the oldest session operation is dropped past the cap, along with its orphaned updates") {
        // Given
        val mocks = SessionQueueMocks()
        val oldestCreate = create(localSessionId = "local-oldest", onesignalId = "local-user")
        val oldestUpdate = update(activeDuration = 1_000, sessionId = "local-oldest")
        val others = (1..MAX_QUEUED_SESSION_OPERATIONS - 2).map { create(localSessionId = "local-$it", onesignalId = "local-user") }
        mocks.enqueueAll(oldestCreate, oldestUpdate, *others.toTypedArray())
        mocks.queuedOperations.size shouldBe MAX_QUEUED_SESSION_OPERATIONS

        // When
        val newest = create(localSessionId = "local-newest", onesignalId = "local-user")
        mocks.enqueueAll(newest)

        // Then
        mocks.queuedOperations shouldContainExactly others + newest
        mocks.storedOperations shouldContainExactly others + newest
    }

    test("updates wait for their create, are translated to the backend session ID, and run in order") {
        // Given
        val mocks = SessionQueueMocks()
        mocks.respondWith(
            { ExecutionResponse(ExecutionResult.SUCCESS, idTranslations = mapOf(LOCAL_SESSION_ID to "server-session")) },
            { ExecutionResponse(ExecutionResult.SUCCESS) },
        )
        val createOp = create()
        val updateOp = update(activeDuration = 10_000)
        mocks.enqueueAll(updateOp, createOp)

        // When
        mocks.operationRepo.start()
        withTimeout(2_000) { while (mocks.executed.size < 2) delay(10) }

        // Then
        mocks.executed shouldContainExactly listOf(createOp, updateOp)
        updateOp.sessionId shouldBe "server-session"
    }

    test("an update enqueued after its create was translated still gets the backend session ID") {
        // Given
        val mocks = SessionQueueMocks()
        mocks.respondWith(
            { ExecutionResponse(ExecutionResult.SUCCESS, idTranslations = mapOf(LOCAL_SESSION_ID to "server-session")) },
            { ExecutionResponse(ExecutionResult.SUCCESS) },
        )
        mocks.enqueueAll(create())
        mocks.operationRepo.start()
        withTimeout(2_000) { while (mocks.executed.size < 1) delay(10) }

        // When
        val lateUpdate = update(activeDuration = 10_000)
        mocks.operationRepo.enqueue(lateUpdate)
        withTimeout(2_000) { while (mocks.executed.size < 2) delay(10) }

        // Then
        mocks.executed[1] shouldBe lateUpdate
        lateUpdate.sessionId shouldBe "server-session"
    }

    test("a create that fails without retry drops its queued updates") {
        // Given
        val mocks = SessionQueueMocks()
        mocks.respondWith({ ExecutionResponse(ExecutionResult.FAIL_NORETRY) })
        val createOp = create()
        val updateOp = update(activeDuration = 10_000)
        mocks.enqueueAll(createOp, updateOp)

        // When
        mocks.operationRepo.start()
        withTimeout(2_000) { while (mocks.queuedOperations.isNotEmpty()) delay(10) }

        // Then
        mocks.executed shouldContainExactly listOf(createOp)
        mocks.storedOperations shouldBe emptyList()
    }

    test("a retried create keeps its idempotency key") {
        // Given
        val mocks = SessionQueueMocks()
        val keys = mutableListOf<String>()
        mocks.respondWith(
            {
                keys.add((it as CreateSessionOperation).idempotencyKey)
                ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = null)
            },
            {
                keys.add((it as CreateSessionOperation).idempotencyKey)
                ExecutionResponse(ExecutionResult.SUCCESS)
            },
        )
        val createOp = create()
        mocks.enqueueAll(createOp)

        // When
        mocks.operationRepo.start()
        withTimeout(2_000) { while (keys.size < 2) delay(10) }

        // Then
        keys shouldContainExactly listOf(createOp.idempotencyKey, createOp.idempotencyKey)
    }

    test("an update for a session whose end is being sent is discarded") {
        // Given
        val mocks = SessionQueueMocks()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        mocks.holdFirstExecution(started, release) { ExecutionResponse(ExecutionResult.SUCCESS) }
        val end = update(activeDuration = 15_000, sessionId = "server-session", endTime = 5_000L)
        mocks.enqueueAll(end)
        mocks.operationRepo.start()
        withTimeout(2_000) { started.await() }

        // When
        mocks.enqueueAll(update(activeDuration = 20_000, sessionId = "server-session"))
        release.complete(Unit)
        withTimeout(2_000) { while (mocks.storedOperations.isNotEmpty()) delay(10) }
        delay(100)

        // Then
        mocks.executed shouldContainExactly listOf(end)
    }

    test("an update for a local session with no pending create is discarded") {
        // Given
        val mocks = SessionQueueMocks()
        mocks.operationRepo.start()
        withTimeout(2_000) { mocks.operationRepo.awaitInitialized() }

        // When
        mocks.enqueueAll(update(activeDuration = 10_000))

        // Then
        mocks.queuedOperations shouldBe emptyList()
        mocks.storedOperations shouldBe emptyList()
    }

    test("an update for a session whose create is being sent is kept and translated") {
        // Given
        val mocks = SessionQueueMocks()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        mocks.holdFirstExecution(started, release) {
            if (it is CreateSessionOperation) {
                ExecutionResponse(ExecutionResult.SUCCESS, idTranslations = mapOf(LOCAL_SESSION_ID to "server-session"))
            } else {
                ExecutionResponse(ExecutionResult.SUCCESS)
            }
        }
        val createOp = create()
        mocks.enqueueAll(createOp)
        mocks.operationRepo.start()
        withTimeout(2_000) { started.await() }

        // When
        val updateOp = update(activeDuration = 10_000)
        mocks.enqueueAll(updateOp)
        release.complete(Unit)
        withTimeout(2_000) { while (mocks.executed.size < 2) delay(10) }

        // Then
        mocks.executed shouldContainExactly listOf(createOp, updateOp)
        updateOp.sessionId shouldBe "server-session"
    }

    test("session operations are not suppressed or purged as anonymous under identity verification") {
        // Given
        val mocks = SessionQueueMocks(ivRequired = true)
        val createOp = create()

        // When
        mocks.enqueueAll(createOp)
        mocks.operationRepo.removeOperationsWithoutExternalId()

        // Then
        mocks.queuedOperations shouldContainExactly listOf(createOp)
        verify(exactly = 0) { mocks.operationModelStore.remove(any()) }
    }

    test("session operations for an anonymous user are discarded at enqueue under identity verification") {
        // Given
        val mocks = SessionQueueMocks(ivRequired = true)
        mocks.operationRepo.start()
        mocks.operationRepo.awaitInitialized()
        val identifiedLogin = LoginUserOperation(APP_ID, "local-identified", "external-id", existingOneSignalId = "local-pending")
        val anonymousCreate = create(onesignalId = "local-anonymous")
        val identifiedCreate = create(localSessionId = "local-other-session", onesignalId = "local-identified")

        // When
        mocks.enqueueAll(identifiedLogin, anonymousCreate, identifiedCreate)

        // Then
        mocks.queuedOperations shouldContainExactly listOf(identifiedCreate)
    }

    test("session operations for an anonymous user are dropped with anonymous operations under identity verification") {
        // Given
        val mocks = SessionQueueMocks(ivRequired = true)
        mocks.enqueueAll(create(onesignalId = "local-anonymous"), update(activeDuration = 10_000))
        mocks.operationRepo.start()
        mocks.operationRepo.awaitInitialized()

        // When
        mocks.operationRepo.removeOperationsWithoutExternalId()

        // Then
        mocks.queuedOperations shouldBe emptyList()
        mocks.storedOperations.filterIsInstance<SessionOperation>() shouldBe emptyList()
    }

    test("translations applied to the queue are also applied to the current session's pinned IDs") {
        // Given
        val mocks = SessionQueueMocks()
        val session =
            mocks.sessionModelStore.model.apply {
                onesignalId = "local-user"
                subscriptionId = "local-subscription"
            }
        mocks.respondWith({
            ExecutionResponse(
                ExecutionResult.SUCCESS,
                idTranslations = mapOf(LOCAL_SESSION_ID to "server-session", "local-user" to "user", "local-subscription" to "subscription"),
            )
        })
        mocks.enqueueAll(create())

        // When
        mocks.operationRepo.start()
        withTimeout(2_000) { while (session.serverSessionId == null) delay(10) }

        // Then
        session.serverSessionId shouldBe "server-session"
        session.onesignalId shouldBe "user"
        session.subscriptionId shouldBe "subscription"
    }

    test("a create for an earlier session does not set the current session's server session ID") {
        // Given
        val mocks = SessionQueueMocks()
        val session = mocks.sessionModelStore.model.apply { sessionId = "newer-session" }
        mocks.respondWith({ ExecutionResponse(ExecutionResult.SUCCESS, idTranslations = mapOf(LOCAL_SESSION_ID to "server-session")) })
        mocks.enqueueAll(create())

        // When
        mocks.operationRepo.start()
        withTimeout(2_000) { while (mocks.executed.isEmpty()) delay(10) }
        delay(100)

        // Then
        session.serverSessionId shouldBe null
    }

    test("a session operation is kept under identity verification while a subscription login creates its user") {
        // Given
        val mocks = SessionQueueMocks(ivRequired = true)
        mocks.operationRepo.start()
        mocks.operationRepo.awaitInitialized()
        val createOp = create(onesignalId = "local-identified")

        // When
        mocks.enqueueAll(LoginUserFromSubscriptionOperation(APP_ID, "local-identified", "external-id", "subscription-id"), createOp)

        // Then
        mocks.queuedOperations shouldContainExactly listOf(createOp)
    }

    test("a subscription translation without the session's user translation leaves the pinned subscription alone") {
        // Given
        val mocks = SessionQueueMocks()
        val session =
            mocks.sessionModelStore.model.apply {
                onesignalId = "local-previous-user"
                subscriptionId = "local-subscription"
            }
        mocks.respondWith({
            ExecutionResponse(
                ExecutionResult.SUCCESS,
                idTranslations = mapOf(LOCAL_SESSION_ID to "server-session", "local-subscription" to "subscription"),
            )
        })
        mocks.enqueueAll(create())

        // When
        mocks.operationRepo.start()
        withTimeout(2_000) { while (session.serverSessionId == null) delay(10) }

        // Then
        session.onesignalId shouldBe "local-previous-user"
        session.subscriptionId shouldBe "local-subscription"
    }

    test("translations before the first session starts are ignored") {
        // Given
        val session = SessionModel()

        // When
        session.translateIds(mapOf("local-user" to "user"))

        // Then
        session.serverSessionId shouldBe null
    }
})
