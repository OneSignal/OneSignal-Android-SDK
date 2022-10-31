package com.onesignal.core.internal.operations

interface IOperationRepo {
    /**
     * Enqueue an operation onto the operation repo.
     *
     * @param operation The operation that should be executed.
     * @param force Whether to force the execution of this operation "immediately".
     */
    fun enqueue(operation: Operation, force: Boolean = false)

    /**
     * Execute the operation provided immediately. Any existing operations on the
     * queue will be analyzed for potential operation grouping/optimizations using
     * this operation as the starting operation.
     *
     * @param operation The operation that will be executed immediately.
     */
    suspend fun execute(operation: Operation)
}
