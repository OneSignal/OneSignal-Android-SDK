package com.onesignal.session.internal.session.operations.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.SessionModelStore
import com.onesignal.session.internal.session.backend.CreateSessionRequest
import com.onesignal.session.internal.session.backend.ISessionsBackendService
import com.onesignal.session.internal.session.backend.UpdateSessionRequest
import com.onesignal.session.internal.session.operations.CreateSessionOperation
import com.onesignal.session.internal.session.operations.UpdateSessionOperation
import com.onesignal.user.internal.operations.impl.states.NewRecordsState
import java.util.concurrent.TimeUnit

internal class SessionOperationExecutor(
    private val sessionsBackend: ISessionsBackendService,
    private val deviceService: IDeviceService,
    private val sessionModelStore: SessionModelStore,
    private val newRecordState: NewRecordsState,
) : IOperationExecutor {
    override val operations: List<String>
        get() = listOf(CREATE_SESSION, UPDATE_SESSION)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse =
        when (val operation = operations.single()) {
            is CreateSessionOperation -> createSession(operation)
            is UpdateSessionOperation -> updateSession(operation)
            else -> error("Unrecognized operation: $operation")
        }

    private suspend fun createSession(operation: CreateSessionOperation): ExecutionResponse {
        val sessionId =
            try {
                sessionsBackend.createSession(
                    operation.appId,
                    CreateSessionRequest(
                        onesignalId = operation.onesignalId,
                        subscriptionId = operation.subscriptionId,
                        deviceType = deviceService.deviceType.value,
                        startTime = TimeUnit.MILLISECONDS.toSeconds(operation.startTime),
                        idempotencyKey = operation.idempotencyKey,
                    ),
                )
            } catch (ex: BackendException) {
                val retryMissing =
                    newRecordState.isInMissingRetryWindow(operation.onesignalId) ||
                        newRecordState.isInMissingRetryWindow(operation.subscriptionId)
                return failure(ex, retryMissing)
            }

        return if (sessionId == null) {
            Logging.warn("SessionOperationExecutor: create session response has no session_id, dropping $operation")
            ExecutionResponse(ExecutionResult.FAIL_NORETRY)
        } else {
            // Updates enqueued from here on read the backend ID from the model; queued ones are translated.
            val session = sessionModelStore.model
            if (session.localSessionId == operation.localSessionId) session.serverSessionId = sessionId
            ExecutionResponse(ExecutionResult.SUCCESS, idTranslations = mapOf(operation.localSessionId to sessionId))
        }
    }

    private suspend fun updateSession(operation: UpdateSessionOperation): ExecutionResponse {
        try {
            sessionsBackend.updateSession(
                operation.appId,
                operation.sessionId,
                UpdateSessionRequest(
                    onesignalId = operation.onesignalId,
                    subscriptionId = operation.subscriptionId,
                    durationSeconds = TimeUnit.MILLISECONDS.toSeconds(operation.activeDuration),
                    idempotencyKey = operation.idempotencyKey,
                    endTime = operation.endTime?.let { TimeUnit.MILLISECONDS.toSeconds(it) },
                ),
            )
        } catch (ex: BackendException) {
            // The create's 202 is processed asynchronously, so a fresh session can briefly 404.
            return failure(ex, retryMissing = newRecordState.isInMissingRetryWindow(operation.sessionId))
        }
        return ExecutionResponse(ExecutionResult.SUCCESS)
    }

    private fun failure(
        ex: BackendException,
        retryMissing: Boolean,
    ): ExecutionResponse {
        val code = ex.statusCode
        val retryable =
            // HttpClient reports offline and other network errors as 0 or -1.
            code <= 0 ||
                code == HTTP_REQUEST_TIMEOUT ||
                code == HTTP_TOO_MANY_REQUESTS ||
                code >= HTTP_SERVER_ERROR ||
                (code == HTTP_NOT_FOUND && retryMissing)
        return if (retryable) {
            ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
        } else {
            ExecutionResponse(ExecutionResult.FAIL_NORETRY, httpStatusCode = code, httpResponse = ex.response)
        }
    }

    companion object {
        const val CREATE_SESSION = "create-session"
        const val UPDATE_SESSION = "update-session"

        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR = 500
    }
}
