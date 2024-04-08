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

    /**
     * Effects the operations given to execute.
     *
     * This function must determine if specific instances of supported
     * operations can be processed together.
     *    - supported = OperationRepo will first check this executor's operations list.
     *    - processed together = Normally means will be sent as a single network call.
     *
     * @param operations The operations to be check if they can be processed together. (TODO: note if the list will always be size 2 or will we pass more?)
     * @return Boolean true if they should be given to execute later, false if not
     */
    fun canProcessTogether(operations: List<Operation>): Boolean
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
     * The operation failed due to a non-retryable error. Pause the operation repo
     * and retry on a new session, giving the SDK a chance to recover from the failed user create.
     */
    FAIL_PAUSE_OPREPO,
}
