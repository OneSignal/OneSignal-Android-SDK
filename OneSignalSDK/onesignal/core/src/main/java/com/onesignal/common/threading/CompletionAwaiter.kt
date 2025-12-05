package com.onesignal.common.threading

import kotlinx.coroutines.CompletableDeferred

/**
 * A completion awaiter for suspend-based waiting.
 * This class allows coroutines to wait for an event (such as SDK initialization) to complete.
 *
 * Usage:
 *   val awaiter = CompletionAwaiter("OneSignal SDK Init")
 *
 *   // Wait for completion (suspend):
 *   awaiter.awaitSuspend()
 *
 *   // Signal completion:
 *   awaiter.complete()
 */
class CompletionAwaiter(
    private val componentName: String = "Component",
) {

    private val suspendCompletion = CompletableDeferred<Unit>()

    /**
     * Completes the awaiter, unblocking all suspend callers.
     */
    fun complete() {
        suspendCompletion.complete(Unit)
    }

    /**
     * Wait for completion using suspend approach (non-blocking for coroutines).
     * Suspends the current coroutine until completion is signaled.
     * Will wait indefinitely until complete() is called.
     */
    suspend fun awaitSuspend() {
        suspendCompletion.await()
    }
}
