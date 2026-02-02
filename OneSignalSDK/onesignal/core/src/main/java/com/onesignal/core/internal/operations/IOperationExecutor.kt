package com.onesignal.core.internal.operations

/**
 * An operation executor is an implementing class that is capable of executing on
 * an [Operation]. When an [Operation] is enqueued via [IOperationRepo.enqueue] it
 * will at some point be executed.  The implementation for [IOperationRepo] will
 * find the best [IOperationExecutor] and call [IOperationExecutor.execute]
 * to execute a group of operations in batch.
 */
interface IOperationExecutor {
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
    suspend fun execute(operations: List<Operation>): ExecutionResponse
}

class ExecutionResponse(
    /**
     * The result of the execution
     */
    val result: ExecutionResult,
    /**
     * The map of id translations that should be applied to any outstanding operations.
     * Within the map the key is the local Id, the value is the remote Id.
     */
    val idTranslations: Map<String, String>? = null,
    /**
     * When specified, any operations that should be prepended to the operation repo.
     */
    val operations: List<Operation>? = null,
    /**
     * Optional Integer value maybe returned from the backend.
     * The module handing this should delay any future requests by this time.
     */
    val retryAfterSeconds: Int? = null,
)

enum class ExecutionResult {
    /**
     * The operation was executed successfully.
     */
    SUCCESS,

    /**
     * The operation group failed but the starting op should be retried split from the group.
     */
    SUCCESS_STARTING_ONLY,

    /**
     * The operation failed but should be retried.
     */
    FAIL_RETRY,

    /**
     * The operation failed and should not be tried again.
     */
    FAIL_NORETRY,

    /**
     * The operation failed because the request was not authorized.  The operation can be
     * retried if authorization can be achieved.
     */
    FAIL_UNAUTHORIZED,

    /**
     * Used in special login case.
     * The operation failed due to a conflict and can be handled.
     */
    FAIL_CONFLICT,

    /**
     * Used in special create user case.
     * The operation failed due to invalid arguments (eg. restricted external ID is used.)
     * We should not retry the operation as the external ID should not be used again.
     */
    FAIL_INVALID_LOGIN,
}
