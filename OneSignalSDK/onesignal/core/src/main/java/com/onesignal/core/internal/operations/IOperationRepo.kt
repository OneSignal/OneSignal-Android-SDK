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
}

// Extension function so the syntax containsInstanceOf<Operation>() can be used over
// containsInstanceOf(Operation::class)
inline fun <reified T : Operation> IOperationRepo.containsInstanceOf(): Boolean = containsInstanceOf(T::class)
