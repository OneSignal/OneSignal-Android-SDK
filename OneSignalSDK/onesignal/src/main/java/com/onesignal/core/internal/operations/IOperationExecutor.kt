package com.onesignal.core.internal.operations

/**
 * An operation executor is an implementing class that is capable of executing on
 * an [Operation]. When an [Operation] is enqueued via [IOperationRepo.enqueue] it
 * will at some point be executed.  The implementation for [IOperationRepo] will
 * find the best [IOperationExecutor] and call [IOperationExecutor.execute]
 * to execute a group of operations in batch.
 */
internal interface IOperationExecutor {

    /**
     * The list of operations that this executor can handle execution.
     */
    val operations: List<String>

    /**
     * Execute the provided operations.
     *
     * @param operations The operations to execute. The first operation drove this executor to receive
     * control.  Subsequent operations in the list existed on the operation repo *and* were considered
     * a match to be executed alongside the starting operation.
     */
    suspend fun execute(operations: List<Operation>)
}
