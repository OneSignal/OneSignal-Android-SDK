package com.onesignal.session.internal.session.operations

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.impl.OperationModelStore
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import com.onesignal.session.internal.session.backend.CreateSessionRequest
import com.onesignal.session.internal.session.backend.ISessionsBackendService
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
        coEvery { backend.createSession(any(), any()) } returns "server-session"
        val executor = SessionOperationExecutor(backend, MockHelper.deviceService())

        // When
        val response = executor.execute(listOf(createOp()))

        // Then
        response.result shouldBe ExecutionResult.SUCCESS
        response.idTranslations shouldBe mapOf("local-session-uuid" to "server-session")
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

    test("network errors, 408, 429, and 5xx are retried with Retry-After") {
        listOf(-1, 0, 408, 429, 500, 503).forEach { statusCode ->
            // Given
            val backend = mockk<ISessionsBackendService>()
            coEvery { backend.createSession(any(), any()) } throws BackendException(statusCode, retryAfterSeconds = 30)
            coEvery { backend.updateSession(any(), any(), any()) } throws BackendException(statusCode, retryAfterSeconds = 30)
            val executor = SessionOperationExecutor(backend, MockHelper.deviceService())

            // When
            val responses = listOf(executor.execute(listOf(createOp())), executor.execute(listOf(updateOp())))

            // Then
            responses.forEach {
                it.result shouldBe ExecutionResult.FAIL_RETRY
                it.retryAfterSeconds shouldBe 30
            }
        }
    }

    test("other 4xx failures are not retried") {
        listOf(400, 401, 403, 404, 409, 410, 413, 422).forEach { statusCode ->
            // Given
            val backend = mockk<ISessionsBackendService>()
            coEvery { backend.createSession(any(), any()) } throws BackendException(statusCode)
            coEvery { backend.updateSession(any(), any(), any()) } throws BackendException(statusCode)
            val executor = SessionOperationExecutor(backend, MockHelper.deviceService())

            // When
            val responses = listOf(executor.execute(listOf(createOp())), executor.execute(listOf(updateOp())))

            // Then
            responses.forEach { it.result shouldBe ExecutionResult.FAIL_NORETRY }
        }
    }

    test("create retries a success response without a session ID") {
        // Given
        val backend = mockk<ISessionsBackendService>()
        coEvery { backend.createSession(any(), any()) } returns null
        val executor = SessionOperationExecutor(backend, MockHelper.deviceService())

        // When
        val response = executor.execute(listOf(createOp()))

        // Then
        response.result shouldBe ExecutionResult.FAIL_RETRY
        response.idTranslations shouldBe null
    }

    test("an operation is dropped after the backend fails it the maximum number of times") {
        listOf<suspend (ISessionsBackendService) -> Unit>(
            { coEvery { it.createSession(any(), any()) } returns null },
            { coEvery { it.createSession(any(), any()) } throws BackendException(503) },
            { coEvery { it.createSession(any(), any()) } throws BackendException(429, retryAfterSeconds = 30) },
        ).forEach { stub ->
            // Given
            val backend = mockk<ISessionsBackendService>().also { stub(it) }
            val executor = SessionOperationExecutor(backend, MockHelper.deviceService())
            val op = createOp()

            // When
            val results = List(SessionOperationExecutor.MAX_FAILED_ATTEMPTS) { executor.execute(listOf(op)).result }

            // Then
            results.dropLast(1).forEach { it shouldBe ExecutionResult.FAIL_RETRY }
            results.last() shouldBe ExecutionResult.FAIL_NORETRY
        }
    }

    test("network errors do not count toward the attempt limit") {
        // Given
        val backend = mockk<ISessionsBackendService>()
        coEvery { backend.updateSession(any(), any(), any()) } throws BackendException(0)
        val executor = SessionOperationExecutor(backend, MockHelper.deviceService())
        val op = updateOp()

        // When
        val results = List(SessionOperationExecutor.MAX_FAILED_ATTEMPTS * 2) { executor.execute(listOf(op)).result }

        // Then
        results.forEach { it shouldBe ExecutionResult.FAIL_RETRY }
        op.failedAttempts shouldBe 0
    }

    test("update sends the duration and end time in seconds") {
        // Given
        val backend = mockk<ISessionsBackendService>()
        coEvery { backend.updateSession(any(), any(), any()) } returns Unit
        val executor = SessionOperationExecutor(backend, MockHelper.deviceService())

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
        duration.failedAttempts = 2
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
        reloadedDuration.failedAttempts shouldBe 2
        (reloaded[2] as UpdateSessionOperation).endTime shouldBe 5_000L
    }
})
