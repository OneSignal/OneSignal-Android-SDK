package com.onesignal.core.internal.operations

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
    fun enqueue(operation: Operation, flush: Boolean = false)

    /**
     * Enqueue an operation onto the operation repo and "wait" until the operation
     * has been executed.
     *
     * @param operation The operation that should be executed.
     * @param flush Whether to force-flush the operation queue.
     *
     * @return true if the operation executed successfully, false otherwise.
     */
    suspend fun enqueueAndWait(operation: Operation, flush: Boolean = false): Boolean
}
