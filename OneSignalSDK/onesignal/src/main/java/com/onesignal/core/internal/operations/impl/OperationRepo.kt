package com.onesignal.core.internal.operations.impl

import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import java.util.UUID

internal class OperationRepo(
    executors: List<IOperationExecutor>,
    private val _operationModelStore: OperationModelStore
) : IOperationRepo, IStartableService {

    private val _executorsMap: Map<String, IOperationExecutor>
    private val _queue = mutableListOf<Operation>()
    private var _queueJob: Deferred<Unit>? = null

    init {
        val executorsMap: MutableMap<String, IOperationExecutor> = mutableMapOf()

        for (executor in executors) {
            for (operation in executor.operations) {
                executorsMap[operation] = executor
            }
        }
        _executorsMap = executorsMap

        for (operation in _operationModelStore.list()) {
            enqueue(operation, false)
        }
    }

    override fun start() {
        // fire up an async job that will run "forever" so we don't hold up the other startable services.
        _queueJob = doWorkAsync()
    }

    override fun enqueue(operation: Operation, force: Boolean) {
        Logging.log(LogLevel.DEBUG, "OperationRepo.enqueue(operation: $operation, force: $force)")

        var isNew = false
        if (!operation.hasProperty(Operation::id.name)) {
            isNew = true
            operation.id = UUID.randomUUID().toString()
        }

        synchronized(_queue) {
            _queue.add(operation)
            if (isNew) {
                _operationModelStore.add(operation)
            }
        }
    }

    override suspend fun execute(operation: Operation) {
        if (!operation.canStartExecute) {
            throw Exception("Cannot execute operation provided: $operation")
        }

        val ops: List<Operation>
        synchronized(_queue) {
            ops = pickGroupableOperations(operation)
        }

        executeOperations(operation, ops)
    }

    private fun doWorkAsync() = GlobalScope.async {
        // This runs forever, until the application is destroyed.
        while (true) {
            try {
                // TODO: Sleep until woken rather than hard loop
                delay(5000L)

                var ops: List<Operation>? = null
                var startingOp: Operation? = null

                synchronized(_queue) {
                    startingOp = _queue.firstOrNull { it.canStartExecute }

                    if (startingOp != null) {
                        _queue.remove(startingOp)
                        _operationModelStore.remove(startingOp!!.id)
                        ops = pickGroupableOperations(startingOp!!)
                    }
                }

                if (startingOp == null || ops == null) {
                    continue
                }

                executeOperations(startingOp!!, ops!!)
            } catch (e: Throwable) {
                Logging.log(LogLevel.ERROR, "Error with Operation work loop", e)
                // TODO: Restart/crash logic
            }
        }
    }

    private suspend fun executeOperations(startingOp: Operation, ops: List<Operation>) {
        try {
            val executor = _executorsMap[startingOp.name]
                ?: throw Exception("Could not find executor for operation ${startingOp.name}")

            executor.execute(ops)
        } catch (e: Throwable) {
            Logging.log(LogLevel.ERROR, "Error attempting to execute operation: $startingOp", e)
        }
    }

    /**
     * Given a starting operation, find and remove from the queue all other operations that
     * can be executed along with the starting operation.  The full list of operations, with
     * the starting operation being first, will be returned.
     *
     * THIS SHOULD BE CALLED WHILE THE QUEUE IS SYNCHRONIZED!!
     */
    private fun pickGroupableOperations(startingOp: Operation): List<Operation> {
        val ops = mutableListOf<Operation>()
        ops.add(startingOp)

        if (startingOp.groupComparisonType == GroupComparisonType.NONE) {
            return ops
        }

        val startingKey =
            if (startingOp.groupComparisonType == GroupComparisonType.CREATE) startingOp.createComparisonKey else startingOp.modifyComparisonKey

        if (_queue.isNotEmpty()) {
            for (item in _queue.toList()) {
                val itemKey =
                    if (startingOp.groupComparisonType == GroupComparisonType.CREATE) item.createComparisonKey else item.modifyComparisonKey

                if (itemKey == startingKey) {
                    _queue.remove(item)
                    _operationModelStore.remove(item.id)
                    ops.add(item)
                }
            }
        }

        return ops
    }
}
