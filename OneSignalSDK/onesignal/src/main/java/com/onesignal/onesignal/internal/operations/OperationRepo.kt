package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging
import kotlinx.coroutines.*
import kotlinx.coroutines.delay

class OperationRepo : IOperationRepo  {
    private val _queue: ArrayDeque<Operation> = ArrayDeque()

    suspend fun start() = coroutineScope {
        launch {
            doWork()
        }
    }

    override fun enqueue(operation: Operation, force: Boolean) {
        Logging.log(LogLevel.DEBUG, "enqueue(operation: $operation)")

        _queue.addLast(operation)
    }

    // this is your first suspending function
    private suspend fun doWork() {
        try {
            while(true) {
                // TODO: Sleep until woken rather than hard loop
                delay(5000L)

                val op = _queue.removeFirstOrNull() ?: continue

                try {
                    op.executeAsync()
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
