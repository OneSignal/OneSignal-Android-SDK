package com.onesignal.onesignal.internal.operations

import com.onesignal.onesignal.logging.LogLevel
import com.onesignal.onesignal.logging.Logging
import kotlinx.coroutines.*
import kotlinx.coroutines.delay

class OperationRepo : IOperationRepo  {
    private val _queue: ArrayDeque<Operation> = ArrayDeque()
    private var _queueJob: Deferred<Unit>? = null

    fun start() {
        // fire up an async job that will run "forever"
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
