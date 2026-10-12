package com.onesignal.session.internal.session.requests

/**
 * Sends sessions API requests in their own persisted queue, so a sessions outage never delays
 * the operation repo's user and subscription operations.
 */
internal interface ISessionRequestQueue {
    fun enqueue(request: SessionRequest)

    /**
     * Enqueues [request] and suspends until it is sent, or dropped without being sent.
     *
     * @return true if the backend accepted [request].
     */
    suspend fun enqueueAndWait(request: SessionRequest): Boolean

    /**
     * Replaces a local OneSignal ID once the backend creates the user.
     */
    fun translateOnesignalId(
        localId: String,
        backendId: String,
    )

    /**
     * Replaces a local subscription ID once the backend creates the subscription, only in requests
     * pinned to [onesignalId], the user it was created for.
     */
    fun translateSubscriptionId(
        localId: String,
        backendId: String,
        onesignalId: String?,
    )
}
