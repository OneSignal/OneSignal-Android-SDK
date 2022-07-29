package com.onesignal.onesignal.core.internal.operations.impl.executors

import com.onesignal.onesignal.core.internal.operations.Operation

/**
 * An operation executor is an implementing class that is capable of executing on
 * an [Operation]. When an [Operation] is enqueued via [IOperationRepo.enqueue] it
 * will at some point be executed.  The implementation for [IOperationRepo] will
 * find the best [IOperationExecutor] and call [IOperationExecutor.executeAsync]
 * to execute the operation.
 */
interface IOperationExecutor  {

    /**
     * The list of operations that this executor can handle execution.
     */
    val operations: List<String>

    /**
     * Execute the provided operation.
     *
     * @param operation The operation to execute.
     */
    suspend fun executeAsync(operation: Operation)
}
