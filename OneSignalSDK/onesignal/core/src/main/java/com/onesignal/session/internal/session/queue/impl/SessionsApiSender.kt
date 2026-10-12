package com.onesignal.session.internal.session.queue.impl

import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.session.backend.CreateSessionRequest
import com.onesignal.session.internal.session.backend.ISessionsBackendService
import com.onesignal.session.internal.session.backend.UpdateSessionRequest
import com.onesignal.session.internal.session.queue.SessionRecord
import com.onesignal.session.internal.session.queue.SessionRecordStore

/**
 * Sends a [SessionRecord]'s create, then its pending update, and applies the result to the record.
 * Record changes happen under [lock], so an update enqueued mid-send isn't lost.
 */
internal class SessionsApiSender(
    private val store: SessionRecordStore,
    private val backend: ISessionsBackendService,
    private val deviceService: IDeviceService,
    private val backoff: SessionsApiBackoff,
    private val lock: Any,
) {
    /** Returns false on a retryable failure, which pauses the queue until the backoff ends. */
    suspend fun send(record: SessionRecord): Boolean {
        if (record.serverSessionId == null && !sendCreate(record)) return false
        val update = synchronized(lock) { record.takeIf { it.serverSessionId != null }?.toUpdateRequest() }
        return update == null || sendUpdate(record, update)
    }

    private suspend fun sendCreate(record: SessionRecord): Boolean {
        val request =
            CreateSessionRequest(
                onesignalId = record.onesignalId,
                subscriptionId = record.subscriptionId,
                deviceType = deviceService.deviceType.value,
                startTime = record.startTime,
                idempotencyKey = record.createKey,
            )
        val serverSessionId =
            try {
                backend.createSession(record.appId, request)
            } catch (e: BackendException) {
                return onFailure(record, e)
            }

        return if (serverSessionId == null) {
            onFailure(record, BackendException(MISSING_SESSION_ID))
        } else {
            synchronized(lock) {
                record.serverSessionId = serverSessionId
                record.failedAttempts = 0
            }
            backoff.recordSuccess()
            true
        }
    }

    private suspend fun sendUpdate(
        record: SessionRecord,
        request: UpdateSessionRequest,
    ): Boolean {
        try {
            backend.updateSession(record.appId, record.serverSessionId!!, request)
        } catch (e: BackendException) {
            return onFailure(record, e)
        }

        synchronized(lock) {
            // A newer update replaced this one while it was in flight; it is sent next.
            if (record.updateKey == request.idempotencyKey) finishUpdate(record)
        }
        backoff.recordSuccess()
        return true
    }

    private fun onFailure(
        record: SessionRecord,
        e: BackendException,
    ): Boolean {
        val answered = e.statusCode != NO_RESPONSE
        if (answered && !isRetryable(e.statusCode)) {
            Logging.warn("SessionsApiSender: dropping request for session ${record.id}, status ${e.statusCode}")
            synchronized(lock) { dropHeadRequest(record) }
            return true
        }
        if (answered) {
            synchronized(lock) {
                record.failedAttempts++
                if (record.failedAttempts >= MAX_ATTEMPTS) {
                    Logging.warn("SessionsApiSender: dropping request for session ${record.id} after $MAX_ATTEMPTS attempts")
                    dropHeadRequest(record)
                }
            }
        }
        backoff.recordFailure(e.retryAfterSeconds)
        return false
    }

    private fun isRetryable(statusCode: Int) =
        statusCode >= SERVER_ERROR || statusCode == REQUEST_TIMEOUT || statusCode == TOO_MANY_REQUESTS ||
            statusCode == MISSING_SESSION_ID

    /** Updates can't be sent without a session, so a failed create takes them with it. */
    private fun dropHeadRequest(record: SessionRecord) {
        if (record.serverSessionId == null) store.remove(record.id) else finishUpdate(record)
    }

    private fun finishUpdate(record: SessionRecord) {
        if (record.hasEnded) {
            store.remove(record.id)
        } else {
            record.updateKey = null
            record.durationSeconds = null
            record.failedAttempts = 0
        }
    }

    private fun SessionRecord.toUpdateRequest(): UpdateSessionRequest? =
        updateKey?.let {
            UpdateSessionRequest(
                onesignalId = onesignalId,
                subscriptionId = subscriptionId,
                durationSeconds = durationSeconds ?: 0L,
                idempotencyKey = it,
                endTime = endTime,
            )
        }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val NO_RESPONSE = 0
        const val REQUEST_TIMEOUT = 408
        const val TOO_MANY_REQUESTS = 429
        const val SERVER_ERROR = 500

        // A create that succeeds without a session ID is retried like a server error.
        const val MISSING_SESSION_ID = -1
    }
}
