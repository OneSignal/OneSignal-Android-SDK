package com.onesignal.session.internal.session.queue

/**
 * Sends sessions API requests on their own path, separate from the operation repo, so a sessions
 * outage never delays user or subscription operations. Requests are persisted and retried with
 * backoff until they succeed, fail permanently, or exceed the attempt cap.
 */
internal interface ISessionsApiQueue {
    fun enqueueCreate(
        sessionId: String,
        appId: String,
        onesignalId: String,
        subscriptionId: String,
        startTimeMillis: Long,
    )

    /**
     * Replaces any unsent update for the session. An end ([endTimeMillis] set) is final: later
     * updates for the session are ignored.
     */
    fun enqueueUpdate(
        sessionId: String,
        durationMillis: Long,
        endTimeMillis: Long? = null,
    )

    /** Sends pending requests now, skipping the failure backoff but not a backend `Retry-After`. */
    suspend fun flush()
}
