package com.onesignal.session.internal.session.operations

import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.impl.OperationModelStore
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.session.internal.session.backend.CreateSessionRequest
import com.onesignal.session.internal.session.backend.ISessionsBackendService
import com.onesignal.session.internal.session.backend.SessionsApiResult
import com.onesignal.session.internal.session.backend.UpdateSessionRequest
import com.onesignal.session.internal.session.operations.impl.SessionOperationExecutor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

private const val APP_ID = "appId"

private fun createOp(localSessionId: String = "local-session-uuid") =
    CreateSessionOperation(
        appId = APP_ID,
        localSessionId = localSessionId,
        onesignalId = "onesignal-id",
        subscriptionId = "subscription-id",
        startTime = 1_700_000_000_123L,
        directAttributionId = "notification-id",
        idempotencyKey = "create-key",
    )

private fun updateOp(endTime: Long? = null) =
    UpdateSessionOperation(
        appId = APP_ID,
        sessionId = "server-session",
        onesignalId = "onesignal-id",
        subscriptionId = "subscription-id",
        activeDuration = 42_900L,
        endTime = endTime,
        idempotencyKey = "update-key",
    )

class SessionOperationExecutorTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("create sends the request in seconds and translates the local session ID") {
        // Given
        val backend = mockk<ISessionsBackendService>()
        coEvery { backend.createSession(any(), any()) } returns SessionsApiResult.Success("server-session")
        val sessionModelStore = MockHelper.sessionModelStore { it.sessionId = "session-uuid" }
        val executor = SessionOperationExecutor(backend, MockHelper.deviceService(), sessionModelStore)

        // When
        val response = executor.execute(listOf(createOp(localSessionId = sessionModelStore.model.localSessionId)))

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        response.idTranslations shouldBe mapOf("local-session-uuid" to "server-session")
        sessionModelStore.model.serverSessionId shouldBe "server-session"
        coVerify {
            backend.createSession(
                APP_ID,
                CreateSessionRequest(
                    onesignalId = "onesignal-id",
                    subscriptionId = "subscription-id",
                    deviceType = 1,
                    startTime = 1_700_000_000L,
                    idempotencyKey = "create-key",
                    directAttributionId = "notification-id",
                ),
            )
        }
    }

    test("create for a session that is no longer current leaves the current session alone") {
        // Given
        val backend = mockk<ISessionsBackendService>()
        coEvery { backend.createSession(any(), any()) } returns SessionsApiResult.Success("server-session")
        val sessionModelStore = MockHelper.sessionModelStore { it.sessionId = "newer-session" }
        val executor = SessionOperationExecutor(backend, MockHelper.deviceService(), sessionModelStore)

        // When
        val response = executor.execute(listOf(createOp(localSessionId = "local-older-session")))

        // Then
        response.idTranslations shouldBe mapOf("local-older-session" to "server-session")
        sessionModelStore.model.serverSessionId shouldBe null
    }

    test("a retryable failure retries and passes Retry-After through") {
        // Given
        val backend = mockk<ISessionsBackendService>()
        coEvery { backend.createSession(any(), any()) } returns SessionsApiResult.Retry(429, retryAfterSeconds = 30)
        val executor = SessionOperationExecutor(backend, MockHelper.deviceService(), MockHelper.sessionModelStore())

        // When
        val response = executor.execute(listOf(createOp()))

        // Then
        response.result shouldBe ExecutionResult.FAIL_RETRY
        response.retryAfterSeconds shouldBe 30
    }

    test("a permanent failure is not retried") {
        // Given
        val backend = mockk<ISessionsBackendService>()
        coEvery { backend.updateSession(any(), any(), any()) } returns SessionsApiResult.Drop(400)
        val executor = SessionOperationExecutor(backend, MockHelper.deviceService(), MockHelper.sessionModelStore())

        // When
        val response = executor.execute(listOf(updateOp()))

        // Then
        response.result shouldBe ExecutionResult.FAIL_NORETRY
    }

    test("update sends the duration and end time in seconds") {
        // Given
        val backend = mockk<ISessionsBackendService>()
        coEvery { backend.updateSession(any(), any(), any()) } returns SessionsApiResult.Success(Unit)
        val executor = SessionOperationExecutor(backend, MockHelper.deviceService(), MockHelper.sessionModelStore())

        // When
        val response = executor.execute(listOf(updateOp(endTime = 1_700_000_100_999L)))

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        coVerify {
            backend.updateSession(
                APP_ID,
                "server-session",
                UpdateSessionRequest(
                    onesignalId = "onesignal-id",
                    subscriptionId = "subscription-id",
                    durationSeconds = 42L,
                    idempotencyKey = "update-key",
                    endTime = 1_700_000_100L,
                ),
            )
        }
    }

    test("operations wait for backend IDs and translate local IDs") {
        // Given
        val create = CreateSessionOperation(APP_ID, "local-session", "local-user", "local-sub", 0L)
        val update = UpdateSessionOperation(APP_ID, "local-session", "local-user", "local-sub", 0L)

        // Then
        create.canStartExecute shouldBe false
        update.canStartExecute shouldBe false

        // When
        val translations = mapOf("local-user" to "user", "local-sub" to "sub")
        create.translateIds(translations)
        update.translateIds(translations)

        // Then
        create.canStartExecute shouldBe true
        update.canStartExecute shouldBe false

        // When
        update.translateIds(mapOf("local-session" to "server-session"))

        // Then
        update.canStartExecute shouldBe true
        update.sessionId shouldBe "server-session"
    }

    test("persisted operations reload with the same idempotency keys") {
        // Given
        val prefs = MockPreferencesService()
        val create = createOp().apply { id = "create-op" }
        val duration = updateOp().apply { id = "duration-op" }
        val end = updateOp(endTime = 5_000L).apply { id = "end-op" }
        OperationModelStore(prefs).apply {
            loadOperations()
            add(create)
            add(duration)
            add(end)
        }

        // When
        val reloaded = OperationModelStore(prefs).apply { loadOperations() }.list().toList()

        // Then
        val reloadedCreate = reloaded[0] as CreateSessionOperation
        reloadedCreate.idempotencyKey shouldBe "create-key"
        reloadedCreate.localSessionId shouldBe "local-session-uuid"
        reloadedCreate.startTime shouldBe 1_700_000_000_123L
        val reloadedDuration = reloaded[1] as UpdateSessionOperation
        reloadedDuration.idempotencyKey shouldBe "update-key"
        reloadedDuration.endTime shouldBe null
        reloadedDuration.activeDuration shouldBe 42_900L
        (reloaded[2] as UpdateSessionOperation).endTime shouldBe 5_000L
    }
})
