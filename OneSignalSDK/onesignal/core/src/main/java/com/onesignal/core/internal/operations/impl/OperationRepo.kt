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
import kotlin.reflect.KClass

internal class OperationRepo(
    executors: List<IOperationExecutor>,
    private val _operationModelStore: OperationModelStore,
    private val _configModelStore: ConfigModelStore,
    private val _time: ITime,
) : IOperationRepo, IStartableService {
    internal class OperationQueueItem(
        val operation: Operation,
        val waiter: WaiterWithValue<Boolean>? = null,
        val executeAt: Long,
        var retries: Int = 0,
    ) {
        override fun toString(): String {
            return Pair(operation.toString(), retries).toString() + "\n"
        }
    }

    private val executorsMap: Map<String, IOperationExecutor>
    private val queue = mutableListOf<OperationQueueItem>()
    private val waiter = WaiterWithValue<Boolean>()
    private var paused = false
    private var lastBatchGrabbedAt: Long = _time.currentTimeMillis - _configModelStore.model.opRepoExecutionInterval

    init {
        val executorsMap: MutableMap<String, IOperationExecutor> = mutableMapOf()

        for (executor in executors) {
            for (operation in executor.operations) {
                executorsMap[operation] = executor
            }
        }
        this.executorsMap = executorsMap

        for (operation in _operationModelStore.list()) {
            internalEnqueue(OperationQueueItem(operation, executeAt = _time.currentTimeMillis), flush = false, addToStore = false)
        }
    }

    private val timeSinceLastBatch get() = _time.currentTimeMillis - lastBatchGrabbedAt
    private val nextScheduleTime get() = _time.currentTimeMillis + (_configModelStore.model.opRepoExecutionInterval - timeSinceLastBatch)

    override fun <T : Operation> containsInstanceOf(type: KClass<T>): Boolean {
        synchronized(queue) {
            return queue.any { type.isInstance(it.operation) }
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
        val executeAt = nextScheduleTime
        println("current time:${_time.currentTimeMillis} ,executeAt:$executeAt")
        internalEnqueue(OperationQueueItem(operation, executeAt = executeAt), flush, true)
    }

    override suspend fun enqueueAndWait(
        operation: Operation,
        flush: Boolean,
    ): Boolean {
        Logging.log(LogLevel.DEBUG, "OperationRepo.enqueueAndWait(operation: $operation, force: $flush)")

        operation.id = UUID.randomUUID().toString()
        val waiter = WaiterWithValue<Boolean>()
        val executeAt = nextScheduleTime
        println("current time:${_time.currentTimeMillis} ,executeAt:$executeAt")
        internalEnqueue(OperationQueueItem(operation, waiter, executeAt = executeAt), flush, true)
        return waiter.waitForWake()
    }

    private fun internalEnqueue(
        queueItem: OperationQueueItem,
        flush: Boolean,
        addToStore: Boolean,
    ) {
        println("current time:internalEnqueue:${_time.currentTimeMillis}")
        synchronized(queue) {
            queue.add(queueItem)
            if (addToStore) {
                _operationModelStore.add(queueItem.operation)
            }
        }

        println("2current time:internalEnqueue:${_time.currentTimeMillis}")
        waiter.wake(flush)
        println("3current time:internalEnqueue:${_time.currentTimeMillis}")
    }

    /**
     * The background processing that will never return.  This should be called on it's own
     * dedicated thread.
     */
    private suspend fun processQueueForever() {
        waitForNewOperationAndExecutionInterval()
        println("HERE")
        while (true) {
            // We shouldn't stall here if we simply are processing the next batch of operations
            //   - That is two operations could have been queued should be processed back to back if so
            // TODO: Write a failing test first
            // Options
            //   1. Save a time stamp for each team, process them without delay if timestamp is old enough
            //   2. Save the last item in the queue, use that as a process up to..
            //      - I think this would have an issue with grouping, as something later in the list could be removed
            //      - Maybe a clone would work? (or a list of ids instead) Then if ANY match we pull them in?
            //      - Or an incurring id, then we simply just store one value, and do a greater than or equal check
            //      - I guess a timestamp really is just an incurrenting id, so this sounds like the best option
//            waitForNewOperationAndExecutionInterval()
            if (paused) {
                Logging.debug("OperationRepo is paused")
                return
            }

            val ops = getNextOps()
            println("processQueueForever:ops:\n$ops")

            if (ops != null) {
                println("before:executeOperations:${_time.currentTimeMillis}")
                executeOperations(ops)
                println("after:executeOperations:${_time.currentTimeMillis}")
                // Allows for any subsequent operations (beyond the first one
                // that woke us) to be enqueued before we pull from the queue.

                println("before:opRepoPostWakeDelay:${_time.currentTimeMillis}, opRepoPostWakeDelay = ${_configModelStore.model.opRepoPostWakeDelay}")
                val beforeDelayTime = _time.currentTimeMillis
                // Thread.sleep(1) takes 2 - 3 ms
                // delay(1) takes 10 - 19 ms
                // delay's variance is fine in production, but it might make tests flaky.
                //   - Test that are timing dependent should probably use a lock instead,
                //     so to do this we could make this a function call,
                //     so a test can override what it does.
                Thread.sleep(_configModelStore.model.opRepoPostWakeDelay)
//                delay(_configModelStore.model.opRepoPostWakeDelay)
                println("after:opRepoPostWakeDelay:tooktime:${_time.currentTimeMillis - beforeDelayTime}")
            } else {
                waitForNewOperationAndExecutionInterval()
            }
            println("Bottom of loop:${_time.currentTimeMillis}")
        }
    }

    /**
     *  Waits until a new operation is enqueued, then wait an additional
     *  amount of time afterwards, so operations can be grouped/batched.
     */
    private suspend fun waitForNewOperationAndExecutionInterval() {
        // 1. Wait for an operation to be enqueued
        var force = false
        println("queue.isEmpty(): ${queue.isEmpty()}")
        if (queue.isEmpty()) {
            force = waiter.waitForWake()
        }

        // 2. Wait at least the time defined in opRepoExecutionInterval
        //    so operations can be grouped, unless one of them used
        //    flush=true (AKA force)
        var lastTime = _time.currentTimeMillis
        var remainingTime = _configModelStore.model.opRepoExecutionInterval
//        println("_configModelStore.model.opRepoExecutionInterval: ${_configModelStore.model.opRepoExecutionInterval}")
        while (!force && remainingTime > 0) {
            withTimeoutOrNull(remainingTime) {
                force = waiter.waitForWake()
            }
            println("force:$force")
            remainingTime -= _time.currentTimeMillis - lastTime
            lastTime = _time.currentTimeMillis
        }
    }

    private suspend fun executeOperations(ops: List<OperationQueueItem>) {
        try {
            val startingOp = ops.first()
            val executor = getExecutor(startingOp.operation)

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
                    waiter.wake(false)
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
                        val queueItem = OperationQueueItem(op, executeAt = _time.currentTimeMillis)
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
        val delayFor = retries * 3_000L
        if (delayFor < 1) return
        Logging.error("Operations being delay for: $delayFor ms")
        delay(delayFor)
    }

    internal fun getNextOps(): List<OperationQueueItem>? {
        return synchronized(queue) {
            println("queue:$queue")
            var lastExecuteAt = 0L
            val startingOp = queue.firstOrNull { lastExecuteAt = it.executeAt; it.operation.canStartExecute && it.executeAt < _time.currentTimeMillis }
            println("_time.currentTimeMillis:${_time.currentTimeMillis}, startingOp:$startingOp, pulled:lastExecuteAt:$lastExecuteAt")

            if (startingOp != null) {
                // TODO: Should this go after executing?
                lastBatchGrabbedAt = _time.currentTimeMillis
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

                if (itemKey == "" && startingKey == "") {
                    throw Exception("Both comparison keys can not be blank!")
                }

                if (itemKey == startingKey) {
                    queue.remove(item)
                    ops.add(item)
                }
            }
        }

        return ops
    }

    private fun getExecutor(operation: Operation): IOperationExecutor {
        return executorsMap[operation.name]
            ?: throw Exception("Could not find executor for operation ${operation.name}")
    }
}
