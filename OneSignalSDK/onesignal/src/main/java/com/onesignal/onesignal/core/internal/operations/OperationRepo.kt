package com.onesignal.onesignal.core.internal.operations

import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.startup.IStartableService
import kotlinx.coroutines.*
import kotlinx.coroutines.delay

class OperationRepo(
    executors: List<IOperationExecutor>
) : IOperationRepo, IStartableService {

    private val _executorsMap: Map<String, IOperationExecutor>
    private val _queue: ArrayDeque<Operation> = ArrayDeque()
    private var _queueJob: Deferred<Unit>? = null

    init {
        val executorsMap: MutableMap<String, IOperationExecutor> = mutableMapOf()

        for(executor in executors) {
            for(operation in executor.operations) {
                executorsMap[operation] = executor
            }
        }
        _executorsMap = executorsMap
    }

    override fun start() {
        // fire up an async job that will run "forever" so we don't hold up the other startable services.
        _queueJob = doWorkAsync()
    }

    override fun enqueue(operation: Operation, force: Boolean) {
        Logging.log(LogLevel.DEBUG, "enqueue(operation: $operation)")

        _queue.addLast(operation)
    }

    private fun doWorkAsync() = GlobalScope.async {
        try {
            while(true) {
                // TODO: Sleep until woken rather than hard loop
                delay(5000L)

                val op = _queue.removeFirstOrNull() ?: continue

                try {
                    val executor = _executorsMap[op.name]
                        ?: throw Exception("Could not find executor for operation ${op.name}")

                    executor.executeAsync(op)
                }
                catch(e: Throwable) {
                    Logging.log(LogLevel.ERROR, "Error attempting to execute operation: $op", e)
                }
            }
        } catch (e: Throwable) {
            Logging.log(LogLevel.ERROR, "Error with Operation work loop", e)
            // TODO: Restart/crash logic
        }
    }
}
