package com.onesignal.common.threading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object OSPrimaryCoroutineScope {
    // Uses computation dispatcher for CPU-intensive operations
    private val computationScope = CoroutineScope(OneSignalDispatchers.Computation)

    /**
     * Executes the given [block] on the computation scope.
     * Uses OneSignal's computation dispatcher for CPU-intensive work.
     */
    fun execute(block: suspend () -> Unit) {
        computationScope.launch {
            block()
        }
    }

    suspend fun waitForIdle() = computationScope.launch { }.join()
}
