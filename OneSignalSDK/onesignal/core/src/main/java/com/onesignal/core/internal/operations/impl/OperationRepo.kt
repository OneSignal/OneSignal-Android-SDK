package com.onesignal.core.internal.operations.impl

import com.onesignal.common.threading.WaiterWithValue
import com.onesignal.common.threading.suspendifyOnThread
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

internal class OperationRepo(
    executors: List<IOperationExecutor>,
    private val _operationModelStore: OperationModelStore,
    private val _configModelStore: ConfigModelStore,
    private val _time: ITime,
) : IOperationRepo, IStartableService {
    internal class OperationQueueItem(
        val operation: Operation,
        val waiter: WaiterWithValue<Boolean>? = null,
        var retries: Int = 0,
    )

    private val executorsMap: Map<String, IOperationExecutor>
    private val queue = mutableListOf<OperationQueueItem>()
    private val waiter = WaiterWithValue<Boolean>()
    private var paused = false
    private var lastTimeOperationGrabbed: Long? = null

    init {
        val executorsMap: MutableMap<String, IOperationExecutor> = mutableMapOf()

        for (executor in executors) {
            for (operation in executor.operations) {
                executorsMap[operation] = executor
            }
        }
        this.executorsMap = executorsMap

        for (operation in _operationModelStore.list()) {
            internalEnqueue(OperationQueueItem(operation), flush = false, addToStore = false)
        }
    }

    override fun start() {
        paused = false
        suspendifyOnThread(name = "OpRepo") {
            processQueueForever()
        }
    }

    override fun enqueue(
        operation: Operation,
        flush: Boolean,
    ) {
        Logging.log(LogLevel.DEBUG, "OperationRepo.enqueue(operation: $operation, flush: $flush)")

        operation.id = UUID.randomUUID().toString()
        internalEnqueue(OperationQueueItem(operation), flush, true)
    }

    override suspend fun enqueueAndWait(
        operation: Operation,
        flush: Boolean,
    ): Boolean {
        Logging.log(LogLevel.DEBUG, "OperationRepo.enqueueAndWait(operation: $operation, force: $flush)")

        operation.id = UUID.randomUUID().toString()
        val waiter = WaiterWithValue<Boolean>()
        internalEnqueue(OperationQueueItem(operation, waiter), flush, true)
        return waiter.waitForWake()
    }

    private fun internalEnqueue(
        queueItem: OperationQueueItem,
        flush: Boolean,
        addToStore: Boolean,
    ) {
        synchronized(queue) {
            queue.add(queueItem)
            if (addToStore) {
                _operationModelStore.add(queueItem.operation)
            }
        }

        waiter.wake(flush)
    }

    /**
     * The background processing that will never return.  This should be called on it's own
     * dedicated thread.
     */
    private suspend fun processQueueForever() {
        waitForNewOperationAndExecutionInterval()
        while (true) {
            if (paused) {
                Logging.debug("OperationRepo is paused")
                return
            }

            val ops = getNextOps()
            Logging.debug("processQueueForever:ops:$ops")

            if (ops != null) {
                executeOperations(ops)
                // Allows for any subsequent operations (beyond the first one
                // that woke us) to be enqueued before we pull from the queue.
                delay(_configModelStore.model.opRepoPostWakeDelay)
            } else {
                waitForNewOperationAndExecutionInterval()
            }
        }
    }

    /**
     *  Waits until a new operation is enqueued, then wait an additional
     *  amount of time afterwards, so operations can be grouped/batched.
     */
    private suspend fun waitForNewOperationAndExecutionInterval() {
        // 1. Wait for an operation to be enqueued
        var force = waiter.waitForWake()

        // 2. Wait at least the time defined in opRepoExecutionInterval
        //    so operations can be grouped, unless one of them used
        //    flush=true (AKA force)
        val startTime = _time.currentTimeMillis
        var lastTime = startTime
        var remainingTime = _configModelStore.model.opRepoExecutionInterval
        while (!force && remainingTime > 0) {
            withTimeoutOrNull(remainingTime) {
                force = waiter.waitForWake()
            }
            remainingTime -= _time.currentTimeMillis - lastTime
            lastTime = _time.currentTimeMillis
        }
    }

    internal suspend fun executeOperations(ops: List<OperationQueueItem>) {
        try {
            val startingOp = ops.first()
            val executor =
                executorsMap[startingOp.operation.name]
                    ?: throw Exception("Could not find executor for operation ${startingOp.operation.name}")

            val operations = ops.map { it.operation }
            val response = executor.execute(operations)

            Logging.debug("OperationRepo: execute response = ${response.result}")

            // if the execution resulted in ID translations, run through the queue so they pick it up.
            // We also run through the ops just executed in case they are re-added to the queue.
            if (response.idTranslations != null) {
                ops.forEach { it.operation.translateIds(response.idTranslations) }
                synchronized(queue) {
                    queue.forEach { it.operation.translateIds(response.idTranslations) }
                }
            }

            when (response.result) {
                ExecutionResult.SUCCESS -> {
                    // on success we remove the operation from the store and wake any waiters
                    ops.forEach { _operationModelStore.remove(it.operation.id) }
                    ops.forEach { it.waiter?.wake(true) }
                }
                ExecutionResult.FAIL_UNAUTHORIZED, // TODO: Need to provide callback for app to reset JWT. For now, fail with no retry.
                ExecutionResult.FAIL_NORETRY,
                ExecutionResult.FAIL_CONFLICT,
                -> {
                    Logging.error("Operation execution failed without retry: $operations")
                    // on failure we remove the operation from the store and wake any waiters
                    ops.forEach { _operationModelStore.remove(it.operation.id) }
                    ops.forEach { it.waiter?.wake(false) }
                }
                ExecutionResult.SUCCESS_STARTING_ONLY -> {
                    // remove the starting operation from the store and wake any waiters, then
                    // add back all but the starting op to the front of the queue to be re-executed
                    _operationModelStore.remove(startingOp.operation.id)
                    startingOp.waiter?.wake(true)
                    synchronized(queue) {
                        ops.filter { it != startingOp }.reversed().forEach { queue.add(0, it) }
                    }
                }
                ExecutionResult.FAIL_RETRY -> {
                    Logging.error("Operation execution failed, retrying: $operations")
                    // add back all operations to the front of the queue to be re-executed.
                    var highestRetries = 0
                    synchronized(queue) {
                        ops.reversed().forEach {
                            if (++it.retries > highestRetries) {
                                highestRetries = it.retries
                            }
                            queue.add(0, it)
                        }
                    }
                    delayBeforeRetry(highestRetries)
                }
                ExecutionResult.FAIL_PAUSE_OPREPO -> {
                    Logging.error("Operation execution failed with eventual retry, pausing the operation repo: $operations")
                    // keep the failed operation and pause the operation repo from executing
                    paused = true
                    // add back all operations to the front of the queue to be re-executed.
                    synchronized(queue) {
                        ops.reversed().forEach { queue.add(0, it) }
                    }
                }
            }

            // if there are operations provided on the result, we need to enqueue them at the
            // beginning of the queue.
            if (response.operations != null) {
                synchronized(queue) {
                    for (op in response.operations.reversed()) {
                        op.id = UUID.randomUUID().toString()
                        val queueItem = OperationQueueItem(op)
                        queue.add(0, queueItem)
                        _operationModelStore.add(0, queueItem.operation)
                    }
                }
            }
        } catch (e: Throwable) {
            Logging.log(LogLevel.ERROR, "Error attempting to execute operation: $ops", e)

            // on failure we remove the operation from the store and wake any waiters
            ops.forEach { _operationModelStore.remove(it.operation.id) }
            ops.forEach { it.waiter?.wake(false) }
        }
    }

    suspend fun delayBeforeRetry(retries: Int) {
        val delayFor = retries * 15_000L
        if (delayFor < 1) return
        Logging.error("Operations being delay for: $delayFor ms")
        delay(delayFor)
    }

    internal fun getNextOps(flush: Boolean): List<OperationQueueItem>? {
        return synchronized(queue) {
            val startingOp =
                queue.firstOrNull {
                    it.operation.canStartExecute &&
                        (
                            // TODO: Time should probably be per operation, or a bucket they go into instead
                            flush ||
                                (_time.currentTimeMillis - (lastTimeOperationGrabbed ?: 0)) >= _configModelStore.model.opRepoExecutionInterval
                        )
                }

            if (startingOp != null) {
                lastTimeOperationGrabbed = _time.currentTimeMillis
                queue.remove(startingOp)
                getGroupableOperations(startingOp)
            } else {
                null
            }
        }
    }

    /**
     * Given a starting operation, find and remove from the queue all other operations that
     * can be executed along with the starting operation.  The full list of operations, with
     * the starting operation being first, will be returned.
     *
     * THIS SHOULD BE CALLED WHILE THE QUEUE IS SYNCHRONIZED!!
     */
    private fun getGroupableOperations(startingOp: OperationQueueItem): List<OperationQueueItem> {
        val ops = mutableListOf<OperationQueueItem>()
        ops.add(startingOp)

        if (startingOp.operation.groupComparisonType == GroupComparisonType.NONE) {
            return ops
        }

        val startingKey =
            if (startingOp.operation.groupComparisonType == GroupComparisonType.CREATE) startingOp.operation.createComparisonKey else startingOp.operation.modifyComparisonKey

        if (queue.isNotEmpty()) {
            for (item in queue.toList()) {
                val itemKey =
                    if (startingOp.operation.groupComparisonType == GroupComparisonType.CREATE) item.operation.createComparisonKey else item.operation.modifyComparisonKey

                if (itemKey == startingKey) {
                    queue.remove(item)
                    ops.add(item)
                }
            }
        }

        return ops
    }
}
