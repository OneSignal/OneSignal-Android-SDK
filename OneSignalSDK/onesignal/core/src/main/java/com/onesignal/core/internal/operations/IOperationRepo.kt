package com.onesignal.core.internal.operations

import kotlin.reflect.KClass

/**
 * The operation queue provides a mechanism to queue one or more [Operation] with the promise
 * it will be executed in a background thread at some point in the future.  Operations are
 * automatically persisted to disk in the event of an application being killed, and will be
 * re-queued when the app is started up again.
 */
interface IOperationRepo {
    /**
     * Enqueue an operation onto the operation repo.
     *
     * @param operation The operation that should be executed.
     * @param flush Whether to force-flush the operation queue.
     */
    fun enqueue(
        operation: Operation,
        flush: Boolean = false,
    )

    /**
     * Enqueue an operation onto the operation repo and "wait" until the operation
     * has been executed.
     *
     * @param operation The operation that should be executed.
     * @param flush Whether to force-flush the operation queue.
     *
     * @return true if the operation executed successfully, false otherwise.
     */
    suspend fun enqueueAndWait(
        operation: Operation,
        flush: Boolean = false,
    ): Boolean

    /**
     * Check if the queue contains a specific operation type
     */
    fun <T : Operation> containsInstanceOf(type: KClass<T>): Boolean

    suspend fun awaitInitialized()

    fun forceExecuteOperations()

    /**
     * Remove all queued operations that have no externalId (anonymous operations).
     * Used by IdentityVerificationService when identity verification is enabled to
     * purge operations that cannot be executed without an authenticated user.
     */
    fun removeOperationsWithoutExternalId()

    /**
     * Register a handler to be called when a runtime 401 Unauthorized response
     * invalidates a JWT. This allows the caller to notify the developer so they
     * can supply a fresh token via [OneSignal.updateUserJwt].
     *
     * The handler is invoked synchronously on the operation repo thread immediately
     * after JWT invalidation and re-queue. It must return quickly; defer heavy work
     * to another thread. The SDK default handler only schedules listener delivery.
     */
    fun setJwtInvalidatedHandler(handler: ((String) -> Unit)?)
}

// Extension function so the syntax containsInstanceOf<Operation>() can be used over
// containsInstanceOf(Operation::class)
inline fun <reified T : Operation> IOperationRepo.containsInstanceOf(): Boolean = containsInstanceOf(T::class)
