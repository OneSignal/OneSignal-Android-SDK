package com.onesignal.session.internal.session.operations

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.impl.OperationModelStore
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.IOMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.session.internal.session.SessionModel
import com.onesignal.session.internal.session.backend.CreateSessionRequest
import com.onesignal.session.internal.session.backend.ISessionsBackendService
import com.onesignal.session.internal.session.backend.UpdateSessionRequest
import com.onesignal.session.internal.session.operations.impl.SessionOperationExecutor
import com.onesignal.user.internal.operations.impl.states.NewRecordsState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

private class Mocks {
    val backend = mockk<ISessionsBackendService>()
    val session = SessionModel().apply { localSessionId = "local-session" }
    val newRecordState = mockk<NewRecordsState>().also { every { it.isInMissingRetryWindow(any()) } returns false }
    val executor =
        SessionOperationExecutor(
            backend,
            MockHelper.deviceService(),
            MockHelper.sessionModelStore().also { every { it.model } returns session },
            newRecordState,
        )
}

private fun create() = CreateSessionOperation("appId", "local-session", "onesignalId", "subscriptionId", startTime = 1_700_000_000_500L)

private fun update(endTime: Long? = null) =
    UpdateSessionOperation("appId", "server-session", "onesignalId", "subscriptionId", activeDuration = 61_900L, endTime = endTime)

class SessionOperationExecutorTests : FunSpec({
    listener(IOMockHelper)

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("create sends the request and translates the local session ID") {
        // Given
        val mocks = Mocks()
        val op = create()
        coEvery { mocks.backend.createSession(any(), any()) } returns "server-session"

        // When
        val response = mocks.executor.execute(listOf(op))

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        response.idTranslations shouldBe mapOf("local-session" to "server-session")
        mocks.session.serverSessionId shouldBe "server-session"
        coVerify {
            mocks.backend.createSession(
                "appId",
                CreateSessionRequest("onesignalId", "subscriptionId", 1, 1_700_000_000L, op.idempotencyKey),
            )
        }
    }

    test("create for an earlier session does not overwrite the current session's server ID") {
        val mocks = Mocks()
        mocks.session.localSessionId = "local-newer"
        coEvery { mocks.backend.createSession(any(), any()) } returns "server-session"

        mocks.executor.execute(listOf(create()))

        mocks.session.serverSessionId shouldBe null
    }

    test("create without a session_id in the response is dropped") {
        val mocks = Mocks()
        coEvery { mocks.backend.createSession(any(), any()) } returns null

        mocks.executor.execute(listOf(create())).result shouldBe ExecutionResult.FAIL_NORETRY
    }

    test("update sends the duration and end time in seconds") {
        val mocks = Mocks()
        val op = update(endTime = 1_700_000_100_999L)
        coEvery { mocks.backend.updateSession(any(), any(), any()) } returns Unit

        mocks.executor.execute(listOf(op)).result shouldBe ExecutionResult.SUCCESS

        coVerify {
            mocks.backend.updateSession(
                "appId",
                "server-session",
                UpdateSessionRequest("onesignalId", "subscriptionId", 61L, op.idempotencyKey, 1_700_000_100L),
            )
        }
    }

    for (status in listOf(-1, 0, 408, 429, 500, 503)) {
        test("status $status is retried with its Retry-After") {
            val mocks = Mocks()
            coEvery { mocks.backend.createSession(any(), any()) } throws BackendException(status, retryAfterSeconds = 7)

            val response = mocks.executor.execute(listOf(create()))

            response.result shouldBe ExecutionResult.FAIL_RETRY
            response.retryAfterSeconds shouldBe 7
        }
    }

    for (status in listOf(400, 401, 403, 404, 409, 422)) {
        test("status $status is dropped") {
            val mocks = Mocks()
            coEvery { mocks.backend.updateSession(any(), any(), any()) } throws BackendException(status)

            mocks.executor.execute(listOf(update())).result shouldBe ExecutionResult.FAIL_NORETRY
        }
    }

    test("update 404 right after the create is retried") {
        val mocks = Mocks()
        every { mocks.newRecordState.isInMissingRetryWindow("server-session") } returns true
        coEvery { mocks.backend.updateSession(any(), any(), any()) } throws BackendException(404)

        mocks.executor.execute(listOf(update())).result shouldBe ExecutionResult.FAIL_RETRY
    }

    test("create 404 while the user is new is retried") {
        val mocks = Mocks()
        every { mocks.newRecordState.isInMissingRetryWindow("onesignalId") } returns true
        coEvery { mocks.backend.createSession(any(), any()) } throws BackendException(404)

        mocks.executor.execute(listOf(create())).result shouldBe ExecutionResult.FAIL_RETRY
    }

    test("idempotency keys survive persistence") {
        // Given
        val store = OperationModelStore(MockPreferencesService())
        val create = create()
        val update = update(endTime = 1L)

        // When
        val restoredCreate = store.create(create.toJSON())
        val restoredUpdate = store.create(update.toJSON())

        // Then
        restoredCreate.shouldBeInstanceOf<CreateSessionOperation>()
        restoredCreate.idempotencyKey shouldBe create.idempotencyKey
        restoredCreate.startTime shouldBe create.startTime
        restoredUpdate.shouldBeInstanceOf<UpdateSessionOperation>()
        restoredUpdate.idempotencyKey shouldBe update.idempotencyKey
        restoredUpdate.endTime shouldBe 1L
    }

    test("each operation gets its own idempotency key") {
        (create().idempotencyKey == create().idempotencyKey) shouldBe false
    }
})
