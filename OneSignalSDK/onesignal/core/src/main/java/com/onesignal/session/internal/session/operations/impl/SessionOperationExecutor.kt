package com.onesignal.session.internal.session.operations.impl

import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.SessionModelStore
import com.onesignal.session.internal.session.backend.CreateSessionRequest
import com.onesignal.session.internal.session.backend.ISessionsBackendService
import com.onesignal.session.internal.session.backend.SessionsApiResult
import com.onesignal.session.internal.session.backend.UpdateSessionRequest
import com.onesignal.session.internal.session.operations.CreateSessionOperation
import com.onesignal.session.internal.session.operations.UpdateSessionOperation
import java.util.concurrent.TimeUnit.MILLISECONDS

internal class SessionOperationExecutor(
    private val sessionsBackend: ISessionsBackendService,
    private val deviceService: IDeviceService,
    private val sessionModelStore: SessionModelStore,
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
        return when (val result = sessionsBackend.createSession(op.appId, request)) {
            is SessionsApiResult.Success -> {
                val serverSessionId = result.value
                // Persisted so updates enqueued after this point, including after a restart, use the backend ID directly.
                val session = sessionModelStore.model
                if (session.localSessionId == op.localSessionId) {
                    session.serverSessionId = serverSessionId
                }
                ExecutionResponse(ExecutionResult.SUCCESS, idTranslations = mapOf(op.localSessionId to serverSessionId))
            }
            is SessionsApiResult.Retry -> retry(result)
            is SessionsApiResult.Drop -> drop("create", result)
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
        return when (val result = sessionsBackend.updateSession(op.appId, op.sessionId, request)) {
            is SessionsApiResult.Success -> ExecutionResponse(ExecutionResult.SUCCESS)
            is SessionsApiResult.Retry -> retry(result)
            is SessionsApiResult.Drop -> drop("update", result)
        }
    }

    private fun retry(result: SessionsApiResult.Retry) = ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = result.retryAfterSeconds)

    private fun drop(
        action: String,
        result: SessionsApiResult.Drop,
    ): ExecutionResponse {
        Logging.warn("SessionOperationExecutor: $action session failed with ${result.statusCode}, dropping")
        return ExecutionResponse(ExecutionResult.FAIL_NORETRY)
    }

    companion object {
        const val CREATE_SESSION = "create-session"
        const val UPDATE_SESSION = "update-session"
    }
}
