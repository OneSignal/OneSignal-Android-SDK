package com.onesignal.core.internal.operations

import com.onesignal.core.internal.operations.impl.OperationModelStore
import com.onesignal.core.internal.operations.impl.OperationRepo
import com.onesignal.core.internal.operations.impl.SessionQueuePolicy
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.CoreInternalMocks
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.session.internal.session.operations.CreateSessionOperation
import com.onesignal.session.internal.session.operations.SessionStateModelStore
import com.onesignal.session.internal.session.operations.UpdateSessionOperation
import com.onesignal.session.internal.session.operations.impl.SessionOperationExecutor
import com.onesignal.user.internal.jwt.JwtRequirement
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.internal.operations.ExecutorMocks.Companion.getNewRecordState
import com.onesignal.user.internal.operations.SetTagOperation
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

private class SessionQueueMocks(
    val prefs: MockPreferencesService = MockPreferencesService(),
) {
    val configModelStore =
        MockHelper.configModelStore {
            it.isInitializedWithRemote = true
            it.useIdentityVerification = JwtRequirement.NOT_REQUIRED
        }

    val operationModelStore = OperationModelStore(prefs)
    val sessionStateStore = SessionStateModelStore(prefs)

    val executed = mutableListOf<Operation>()
    var respond: (Operation) -> ExecutionResponse = { ExecutionResponse(ExecutionResult.SUCCESS) }

    val executor: IOperationExecutor =
        mockk<IOperationExecutor>().also {
            every { it.operations } returns listOf(SessionOperationExecutor.CREATE_SESSION, SessionOperationExecutor.UPDATE_SESSION)
            coEvery { it.execute(any()) } answers {
                val op = firstArg<List<Operation>>().single()
                executed.add(op)
                respond(op)
            }
        }

    var identityVerificationService = CoreInternalMocks.identityVerificationService()

    val operationRepo: OperationRepo by lazy {
        spyk(
            OperationRepo(
                listOf(executor),
                operationModelStore,
                configModelStore,
                MockHelper.systemTime(),
                getNewRecordState(configModelStore),
                JwtTokenStore(MockPreferencesService()),
                identityVerificationService,
                sessionStateStore,
            ),
            recordPrivateCalls = true,
        )
    }

    fun queued() = synchronized(operationRepo.queue) { operationRepo.queue.map { it.operation } }

    fun enqueueAll(vararg ops: Operation) {
        ops.forEach { operationRepo.enqueue(it) }
        verify(timeout = 1_000, exactly = ops.size) {
            operationRepo["enqueueNew"](any<OperationRepo.OperationQueueItem>(), any<Boolean>())
        }
    }
}

private fun create(
    localSessionId: String = "local-s1",
    onesignalId: String = "onesignalId",
    externalId: String? = null,
) = CreateSessionOperation("appId", localSessionId, onesignalId, "subscriptionId", startTime = 1_000L, externalId)

private fun update(
    sessionId: String = "server-s1",
    activeDuration: Long = 1_000L,
    endTime: Long? = null,
    onesignalId: String = "onesignalId",
) = UpdateSessionOperation("appId", sessionId, onesignalId, "subscriptionId", activeDuration, endTime)

private fun item(op: Operation) = OperationRepo.OperationQueueItem(op, bucket = 0)

private suspend fun awaitExecuted(
    mocks: SessionQueueMocks,
    count: Int,
) = withTimeout(5_000) {
    while (mocks.executed.size < count) delay(10)
}

class SessionOperationQueueTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("duration-only updates collapse to the highest duration") {
        val mocks = SessionQueueMocks()

        mocks.enqueueAll(update(activeDuration = 2_000L), update(activeDuration = 5_000L), update(activeDuration = 4_000L))

        val queued = mocks.queued().single() as UpdateSessionOperation
        queued.activeDuration shouldBe 5_000L
        mocks.operationModelStore.list().map { it.id } shouldContainExactly listOf(queued.id)
    }

    test("an end replaces an unsent update and keeps its duration") {
        val mocks = SessionQueueMocks()

        mocks.enqueueAll(update(activeDuration = 5_000L), update(activeDuration = 3_000L, endTime = 9L))

        val queued = mocks.queued().single() as UpdateSessionOperation
        queued.endTime shouldBe 9L
        queued.activeDuration shouldBe 5_000L
    }

    test("an update after a queued end is dropped") {
        val mocks = SessionQueueMocks()

        mocks.enqueueAll(update(endTime = 9L), update(activeDuration = 8_000L))

        val queued = mocks.queued().single() as UpdateSessionOperation
        queued.endTime shouldBe 9L
    }

    test("updates for different sessions are not coalesced") {
        val mocks = SessionQueueMocks()

        mocks.enqueueAll(update(sessionId = "s1"), update(sessionId = "s2"))

        mocks.queued().size shouldBe 2
    }

    test("cap drops the oldest session with all its operations") {
        val mocks = SessionQueueMocks()
        val max = SessionQueuePolicy.MAX_QUEUED_SESSION_OPERATIONS
        val others = (1 until max).map { create(localSessionId = "local-other-$it") }

        mocks.enqueueAll(create(), update(sessionId = "local-s1"), *others.toTypedArray())

        mocks.queued() shouldContainExactly others
        mocks.operationModelStore.list().size shouldBe others.size
    }

    test("non-session operations do not count toward the cap") {
        val policy = SessionQueuePolicy(maxQueued = 2)
        val queue = mutableListOf(item(create(localSessionId = "local-a")), item(create(localSessionId = "local-b")))

        val result = policy.onEnqueue(queue, item(SetTagOperation("appId", "onesignalId", null, "key", "value")))

        result.accept shouldBe true
        result.removed shouldBe emptyList()
        queue.size shouldBe 2
    }

    test("a coalesced update keeps the backoff of the update it replaces") {
        val policy = SessionQueuePolicy()
        val backedOff = item(update()).apply {
            retries = 3
            retryAt = 60_000L
        }
        val incoming = item(update(activeDuration = 2_000L))

        policy.onEnqueue(mutableListOf(backedOff), incoming)

        incoming.retries shouldBe 3
        incoming.retryAt shouldBe 60_000L
    }

    test("an update after an end that is executing is dropped") {
        val policy = SessionQueuePolicy()
        val queue = mutableListOf<OperationRepo.OperationQueueItem>()
        policy.onEnqueue(queue, item(update(endTime = 9L))).accept shouldBe true

        // The end leaves the queue while it executes.
        policy.onEnqueue(queue, item(update(activeDuration = 8_000L))).accept shouldBe false
    }

    test("an end recorded under the local ID still drops updates after the create succeeds") {
        val policy = SessionQueuePolicy()
        val queue = mutableListOf<OperationRepo.OperationQueueItem>()
        policy.onEnqueue(queue, item(create()))
        policy.onEnqueue(queue, item(update(sessionId = "local-s1", endTime = 9L))).accept shouldBe true

        policy.recordTranslations(mapOf("local-s1" to "server-s1"))

        policy.onEnqueue(mutableListOf(), item(update(sessionId = "server-s1"))).accept shouldBe false
    }

    test("an update for a session whose create was dropped is rejected") {
        val policy = SessionQueuePolicy()

        policy.onDropped(mutableListOf(), create())

        policy.onEnqueue(mutableListOf(), item(update(sessionId = "local-s1"))).accept shouldBe false
    }

    test("an update with a local session ID and no create is rejected") {
        SessionQueuePolicy().onEnqueue(mutableListOf(), item(update(sessionId = "local-s1"))).accept shouldBe false
    }

    test("an update carries the IDs its create was sent with") {
        val policy = SessionQueuePolicy()
        val queue = mutableListOf<OperationRepo.OperationQueueItem>()
        val create = create(onesignalId = "local-user")
        policy.onEnqueue(queue, item(create))
        val queued = update(sessionId = "local-s1", onesignalId = "user-b")

        policy.onEnqueue(queue, item(queued))

        queued.onesignalId shouldBe "local-user"
    }

    test("an update after its create succeeded carries the create's IDs") {
        val policy = SessionQueuePolicy()
        val create = create(onesignalId = "user-a")
        policy.onEnqueue(mutableListOf(), item(create))
        policy.recordTranslations(mapOf("local-s1" to "server-s1"))
        val later = update(sessionId = "server-s1", onesignalId = "user-b")

        policy.onEnqueue(mutableListOf(), item(later))

        later.onesignalId shouldBe "user-a"
    }

    test("an update after a restart carries the IDs of a create from before it") {
        val prefs = MockPreferencesService()
        SessionQueuePolicy(sessions = SessionStateModelStore(prefs)).onEnqueue(mutableListOf(), item(create(onesignalId = "user-a")))
        val later = update(sessionId = "local-s1", onesignalId = "user-b")

        val accepted = SessionQueuePolicy(sessions = SessionStateModelStore(prefs)).onEnqueue(mutableListOf(), item(later)).accept

        accepted shouldBe true
        later.onesignalId shouldBe "user-a"
    }

    test("an update after an end that already left the queue is rejected after a restart") {
        val prefs = MockPreferencesService()
        SessionQueuePolicy(sessions = SessionStateModelStore(prefs)).onEnqueue(mutableListOf(), item(update(endTime = 2_000L)))

        SessionQueuePolicy(sessions = SessionStateModelStore(prefs)).onEnqueue(mutableListOf(), item(update())).accept shouldBe false
    }

    test("an update after a restart is sent with the backend session ID of a create from before it") {
        val prefs = MockPreferencesService()
        SessionQueuePolicy(sessions = SessionStateModelStore(prefs)).apply {
            onEnqueue(mutableListOf(), item(create()))
            recordTranslations(mapOf("local-s1" to "server-s1"))
        }
        val later = update(sessionId = "local-s1")

        SessionQueuePolicy(sessions = SessionStateModelStore(prefs)).onEnqueue(mutableListOf(), item(later))

        later.sessionId shouldBe "server-s1"
    }

    test("the session state cap never drops a session with operations still queued") {
        val policy = SessionQueuePolicy(maxQueued = 2)
        val queue = mutableListOf<OperationRepo.OperationQueueItem>()
        val queued = item(create(localSessionId = "local-a"))
        policy.onEnqueue(queue, queued)
        queue.add(queued)

        policy.onEnqueue(queue, item(create(localSessionId = "local-b")))
        policy.onEnqueue(queue, item(create(localSessionId = "local-c")))

        policy.onEnqueue(queue, item(update(sessionId = "local-a"))).accept shouldBe true
        policy.onEnqueue(queue, item(update(sessionId = "local-b"))).accept shouldBe false
    }

    test("session operations are not stored before privacy consent") {
        val mocks = SessionQueueMocks()
        mocks.configModelStore.model.consentRequired = true
        mocks.configModelStore.model.consentGiven = false

        mocks.enqueueAll(create(), update())

        mocks.queued() shouldBe emptyList()
        mocks.operationModelStore.list() shouldBe emptyList()
    }

    test("an update for a session whose create was rejected is rejected") {
        var awaitingConsent = true
        val policy = SessionQueuePolicy(awaitingConsent = { awaitingConsent })
        policy.onEnqueue(mutableListOf(), item(create())).accept shouldBe false

        awaitingConsent = false

        policy.onEnqueue(mutableListOf(), item(update(sessionId = "local-s1"))).accept shouldBe false
    }

    test("a session create does not stall other operations for the post-create delay") {
        val mocks = SessionQueueMocks()
        mocks.configModelStore.model.opRepoPostCreateDelay = 60_000L
        mocks.respond = { op ->
            if (op is CreateSessionOperation) {
                ExecutionResponse(ExecutionResult.SUCCESS, idTranslations = mapOf(op.localSessionId to "server-s1"))
            } else {
                ExecutionResponse(ExecutionResult.SUCCESS)
            }
        }

        mocks.enqueueAll(create(), update(sessionId = "server-other"))
        mocks.operationRepo.start()
        awaitExecuted(mocks, 2)

        (mocks.executed[1] as UpdateSessionOperation).sessionId shouldBe "server-other"
    }

    test("an update waits for its create and is sent with the backend session ID") {
        val mocks = SessionQueueMocks()
        mocks.respond = { op ->
            if (op is CreateSessionOperation) {
                ExecutionResponse(ExecutionResult.SUCCESS, idTranslations = mapOf(op.localSessionId to "server-s1"))
            } else {
                ExecutionResponse(ExecutionResult.SUCCESS)
            }
        }

        mocks.enqueueAll(create(), update(sessionId = "local-s1"))
        mocks.operationRepo.start()
        awaitExecuted(mocks, 2)

        mocks.executed[0].shouldBeCreate()
        (mocks.executed[1] as UpdateSessionOperation).sessionId shouldBe "server-s1"
    }

    test("an update built from the local ID after its create succeeded is still sent") {
        val mocks = SessionQueueMocks()
        val created = CompletableDeferred<Unit>()
        mocks.respond = { op ->
            if (op is CreateSessionOperation) {
                created.complete(Unit)
                ExecutionResponse(ExecutionResult.SUCCESS, idTranslations = mapOf(op.localSessionId to "server-s1"))
            } else {
                ExecutionResponse(ExecutionResult.SUCCESS)
            }
        }
        mocks.operationRepo.start()
        mocks.operationRepo.enqueue(create())
        created.await()
        awaitExecuted(mocks, 1)

        mocks.operationRepo.enqueue(update(sessionId = "local-s1"))
        awaitExecuted(mocks, 2)

        (mocks.executed[1] as UpdateSessionOperation).sessionId shouldBe "server-s1"
    }

    test("a dropped create drops its queued updates") {
        val mocks = SessionQueueMocks()
        mocks.respond = { ExecutionResponse(ExecutionResult.FAIL_NORETRY) }

        mocks.enqueueAll(create(), update(sessionId = "local-s1"), update(sessionId = "server-other"))
        mocks.operationRepo.start()
        awaitExecuted(mocks, 2)

        mocks.executed.map { it.applyToRecordId } shouldContainExactly listOf("local-s1", "server-other")
        mocks.queued() shouldBe emptyList()
        mocks.operationModelStore.list() shouldBe emptyList()
    }

    test("a create waits for a backend onesignalId") {
        val op = create(onesignalId = "local-user")
        op.canStartExecute shouldBe false

        op.translateIds(mapOf("local-user" to "user"))

        op.canStartExecute shouldBe true
        op.onesignalId shouldBe "user"
    }

    test("a persisted create replays on the next launch with the same idempotency key") {
        // Given
        val prefs = MockPreferencesService()
        val op = create().apply { id = "op-id" }
        OperationModelStore(prefs).apply { loadOperations() }.add(op)

        // When
        val mocks = SessionQueueMocks(prefs)
        mocks.operationRepo.start()
        awaitExecuted(mocks, 1)

        // Then
        (mocks.executed.single() as CreateSessionOperation).idempotencyKey shouldBe op.idempotencyKey
    }

    test("session operations are kept for anonymous users when identity verification is required") {
        val mocks = SessionQueueMocks()
        mocks.configModelStore.model.useIdentityVerification = JwtRequirement.REQUIRED
        mocks.identityVerificationService = CoreInternalMocks.identityVerificationService(newCodePathsRun = true, ivBehaviorActive = true)

        mocks.enqueueAll(create())
        mocks.operationRepo.removeOperationsWithoutExternalId()

        mocks.queued().size shouldBe 1
    }

    test("a create for an anonymous user that was never created is purged with its updates under identity verification") {
        val mocks = SessionQueueMocks()
        mocks.identityVerificationService = CoreInternalMocks.identityVerificationService(newCodePathsRun = true, ivBehaviorActive = true)
        val kept = create(localSessionId = "local-s2", onesignalId = "local-user", externalId = "alice")

        mocks.enqueueAll(create(onesignalId = "local-anon"), update(sessionId = "local-s1"), kept)
        mocks.configModelStore.model.useIdentityVerification = JwtRequirement.REQUIRED
        mocks.operationRepo.removeOperationsWithoutExternalId()

        mocks.queued() shouldContainExactly listOf(kept)
        mocks.operationModelStore.list() shouldContainExactly listOf(kept)
    }

    test("a create for an anonymous user that was never created is not enqueued under identity verification") {
        val mocks = SessionQueueMocks()
        mocks.configModelStore.model.useIdentityVerification = JwtRequirement.REQUIRED
        mocks.identityVerificationService = CoreInternalMocks.identityVerificationService(newCodePathsRun = true, ivBehaviorActive = true)

        mocks.operationRepo.enqueueAndAwaitResult(create(onesignalId = "local-anon")).success shouldBe false

        mocks.queued() shouldBe emptyList()
        mocks.operationModelStore.list() shouldBe emptyList()
    }
})

private fun Operation.shouldBeCreate() = (this is CreateSessionOperation) shouldBe true
