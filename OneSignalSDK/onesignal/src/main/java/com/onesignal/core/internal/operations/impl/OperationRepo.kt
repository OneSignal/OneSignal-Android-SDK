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
    private val _time: ITime
) : IOperationRepo, IStartableService {

    private class OperationQueueItem(
        val operation: Operation,
        val waiter: WaiterWithValue<Boolean>? = null
    )

    private val _executorsMap: Map<String, IOperationExecutor>
    private val _queue = mutableListOf<OperationQueueItem>()
    private val _waiter = WaiterWithValue<Boolean>()

    init {
        val executorsMap: MutableMap<String, IOperationExecutor> = mutableMapOf()

        for (executor in executors) {
            for (operation in executor.operations) {
                executorsMap[operation] = executor
            }
        }
        _executorsMap = executorsMap

        for (operation in _operationModelStore.list()) {
            internalEnqueue(OperationQueueItem(operation), flush = false, addToStore = false)
        }
    }

    override fun start() {
        suspendifyOnThread {
            processQueueForever()
        }
    }

    override fun enqueue(operation: Operation, flush: Boolean) {
        Logging.log(LogLevel.DEBUG, "OperationRepo.enqueue(operation: $operation, flush: $flush)")

        operation.id = UUID.randomUUID().toString()
        internalEnqueue(OperationQueueItem(operation), flush, true)
    }

    override suspend fun enqueueAndWait(operation: Operation, flush: Boolean): Boolean {
        Logging.log(LogLevel.DEBUG, "OperationRepo.enqueueAndWait(operation: $operation, force: $flush)")

        operation.id = UUID.randomUUID().toString()
        val waiter = WaiterWithValue<Boolean>()
        internalEnqueue(OperationQueueItem(operation, waiter), flush, true)
        return waiter.waitForWake()
    }

    private fun internalEnqueue(queueItem: OperationQueueItem, flush: Boolean, addToStore: Boolean) {
        synchronized(_queue) {
            _queue.add(queueItem)
            if (addToStore) {
                _operationModelStore.add(queueItem.operation)
            }
        }

        _waiter.wake(flush)
    }

    /**
     * The background processing that will never return.  This should be called on it's own
     * dedicated thread.
     */
    private suspend fun processQueueForever() {
        var lastSyncTime = _time.currentTimeMillis
        var force = false

        // This runs forever, until the application is destroyed.
        while (true) {
            try {
                var ops: List<OperationQueueItem>? = null

                synchronized(_queue) {
                    val startingOp = _queue.firstOrNull { it.operation.canStartExecute }

                    if (startingOp != null) {
                        _queue.remove(startingOp)
                        ops = getGroupableOperations(startingOp)
                    }
                }

                // if the queue is empty at this point, we are no longer in force flush mode. We
                // check this now so if the execution is unsuccessful with retry, we don't find ourselves
                // continuously retrying without delaying.
                if (_queue.isEmpty()) {
                    force = false
                }

                if (ops != null) {
                    executeOperations(ops!!)
                }

                if (!force) {
                    // potentially delay to prevent this from constant IO if a bunch of
                    // operations are set sequentially.
                    val newTime = _time.currentTimeMillis

                    val delay = lastSyncTime - newTime + _configModelStore.model.opRepoExecutionInterval
                    lastSyncTime = newTime

                    if (delay > 0) {
                        withTimeoutOrNull(delay) {
                            // wait to be woken up for the next pass
                            force = _waiter.waitForWake()
                        }

                        // This secondary delay allows for any subsequent operations (beyond the first one
                        // that woke us) to be enqueued before we pull from the queue.
                        delay(_configModelStore.model.opRepoPostWakeDelay)
                    }
                }
            } catch (e: Throwable) {
                Logging.log(LogLevel.ERROR, "Error occurred with Operation work loop", e)
            }
        }
    }

    private suspend fun executeOperations(ops: List<OperationQueueItem>) {
        try {
            val startingOp = ops.first()
            val executor = _executorsMap[startingOp.operation.name]
                ?: throw Exception("Could not find executor for operation ${startingOp.operation.name}")

            val operations = ops.map { it.operation }
            val response = executor.execute(operations)

            Logging.debug("OperationRepo: execute response = ${response.result}")

            // if the execution resulted in ID translations, run through the queue so they pick it up.
            // We also run through the ops just executed in case they are re-added to the queue.
            if (response.idTranslations != null) {
                ops.forEach { it.operation.translateIds(response.idTranslations) }
                synchronized(_queue) {
                    _queue.forEach { it.operation.translateIds(response.idTranslations) }
                }
            }

            when (response.result) {
                ExecutionResult.SUCCESS -> {
                    // on success we remove the operation from the store and wake any waiters
                    ops.forEach { _operationModelStore.remove(it.operation.id) }
                    ops.forEach { it.waiter?.wake(true) }
                }
                ExecutionResult.FAIL_NORETRY -> {
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
                    synchronized(_queue) {
                        ops.filter { it != startingOp }.reversed().forEach { _queue.add(0, it) }
                    }
                }
                ExecutionResult.FAIL_RETRY -> {
                    // add back all operations to the front of the queue to be re-executed.
                    synchronized(_queue) {
                        ops.reversed().forEach { _queue.add(0, it) }
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

        if (_queue.isNotEmpty()) {
            for (item in _queue.toList()) {
                val itemKey =
                    if (startingOp.operation.groupComparisonType == GroupComparisonType.CREATE) item.operation.createComparisonKey else item.operation.modifyComparisonKey

                if (itemKey == startingKey) {
                    _queue.remove(item)
                    ops.add(item)
                }
            }
        }

        return ops
    }
}
