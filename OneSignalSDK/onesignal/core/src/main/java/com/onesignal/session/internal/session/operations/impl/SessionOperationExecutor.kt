package com.onesignal.session.internal.session.operations.impl

import com.onesignal.common.NetworkUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.backend.CreateSessionRequest
import com.onesignal.session.internal.session.backend.ISessionsBackendService
import com.onesignal.session.internal.session.backend.UpdateSessionRequest
import com.onesignal.session.internal.session.operations.CreateSessionOperation
import com.onesignal.session.internal.session.operations.SessionOperation
import com.onesignal.session.internal.session.operations.UpdateSessionOperation
import java.util.concurrent.TimeUnit.MILLISECONDS

internal class SessionOperationExecutor(
    private val sessionsBackend: ISessionsBackendService,
    private val deviceService: IDeviceService,
) : IOperationExecutor {
    override val operations: List<String>
        get() = listOf(CREATE_SESSION, UPDATE_SESSION)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse =
        when (val operation = operations.first()) {
            is CreateSessionOperation -> createSession(operation)
            is UpdateSessionOperation -> updateSession(operation)
            else -> throw IllegalArgumentException("Unrecognized operation: $operation")
        }

    private suspend fun createSession(op: CreateSessionOperation): ExecutionResponse {
        val request =
            CreateSessionRequest(
                onesignalId = op.onesignalId,
                subscriptionId = op.subscriptionId,
                deviceType = deviceService.deviceType.value,
                startTime = MILLISECONDS.toSeconds(op.startTime),
                idempotencyKey = op.idempotencyKey,
                directAttributionId = op.directAttributionId,
            )
        val serverSessionId =
            try {
                sessionsBackend.createSession(op.appId, request)
            } catch (ex: BackendException) {
                return failure(op, "create", ex)
            }
        return if (serverSessionId == null) {
            // Retrying with the same idempotency key lets the backend return the session it already created.
            Logging.warn("SessionOperationExecutor: create session response is missing data.session_id")
            retryOrDrop(op, "create", backendResponded = true, retryAfterSeconds = null)
        } else {
            // OperationRepo also writes this translation into the current session's pinned IDs.
            ExecutionResponse(ExecutionResult.SUCCESS, idTranslations = mapOf(op.localSessionId to serverSessionId))
        }
    }

    private suspend fun updateSession(op: UpdateSessionOperation): ExecutionResponse {
        val request =
            UpdateSessionRequest(
                onesignalId = op.onesignalId,
                subscriptionId = op.subscriptionId,
                durationSeconds = MILLISECONDS.toSeconds(op.activeDuration),
                idempotencyKey = op.idempotencyKey,
                endTime = op.endTime?.let { MILLISECONDS.toSeconds(it) },
            )
        try {
            sessionsBackend.updateSession(op.appId, op.sessionId, request)
        } catch (ex: BackendException) {
            return failure(op, "update", ex)
        }
        return ExecutionResponse(ExecutionResult.SUCCESS)
    }

    private fun failure(
        op: SessionOperation,
        action: String,
        ex: BackendException,
    ): ExecutionResponse {
        val code = ex.statusCode
        // RETRYABLE also covers 4xx codes NetworkUtils doesn't list, like 413 and 422. Those won't succeed on retry.
        val retryable =
            NetworkUtils.getResponseStatusType(code) == NetworkUtils.ResponseStatusType.RETRYABLE &&
                (code !in CLIENT_ERROR_CODES || code in RETRYABLE_CLIENT_ERROR_CODES)
        if (!retryable) {
            Logging.warn("SessionOperationExecutor: $action session failed with $code, dropping")
            return ExecutionResponse(ExecutionResult.FAIL_NORETRY)
        }
        // A status of 0 or less means no response, e.g. offline, which must keep retrying until reconnect.
        return retryOrDrop(op, action, backendResponded = code > 0, retryAfterSeconds = ex.retryAfterSeconds)
    }

    /**
     * OperationRepo retries at the front of the queue, so a backend that keeps failing blocks every
     * other operation. Attempts it answered are capped; attempts that never reached it are not.
     */
    private fun retryOrDrop(
        op: SessionOperation,
        action: String,
        backendResponded: Boolean,
        retryAfterSeconds: Int?,
    ): ExecutionResponse {
        if (backendResponded && ++op.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            Logging.warn("SessionOperationExecutor: $action session failed ${op.failedAttempts} times, dropping")
            return ExecutionResponse(ExecutionResult.FAIL_NORETRY)
        }
        return ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = retryAfterSeconds)
    }

    companion object {
        const val CREATE_SESSION = "create-session"
        const val UPDATE_SESSION = "update-session"

        internal const val MAX_FAILED_ATTEMPTS = 5

        private val CLIENT_ERROR_CODES = 400..499

        // Request Timeout and Too Many Requests
        private val RETRYABLE_CLIENT_ERROR_CODES = setOf(408, 429)
    }
}
