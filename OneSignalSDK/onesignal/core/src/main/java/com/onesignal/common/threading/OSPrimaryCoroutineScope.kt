package com.onesignal.common.threading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext

object OSPrimaryCoroutineScope {
    // CoroutineScope tied to the main thread
    private val mainScope = CoroutineScope(newSingleThreadContext(name = "OSPrimaryCoroutineScope"))

    /**
     * Executes the given [block] on the OS primary coroutine scope.
     */
    fun execute(block: suspend () -> Unit) {
        mainScope.launch {
            block()
        }
    }
}
