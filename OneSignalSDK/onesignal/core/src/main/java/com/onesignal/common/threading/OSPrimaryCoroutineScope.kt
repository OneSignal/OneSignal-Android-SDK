package com.onesignal.common.threading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object OSPrimaryCoroutineScope {
    // CoroutineScope tied to the main thread
    private val mainScope = CoroutineScope(newSingleThreadContext(name = "OSPrimaryCoroutineScope"))
    private val activeJobs = mutableSetOf<Job>()

    /**
     * Executes the given [block] on the OS primary coroutine scope.
     */
    fun execute(block: suspend () -> Unit) {
        val job =
            mainScope.launch {
                block()
            }
        activeJobs.add(job)
        job.invokeOnCompletion {
            activeJobs.remove(job)
        }
    }

    /**
     * Suspends until there are no active tasks running in the mainScope.
     * This is useful for testing or ensuring all background work is complete.
     */
    suspend fun waitForIdle() =
        suspendCancellableCoroutine { continuation ->
            if (activeJobs.isEmpty()) {
                continuation.resume(Unit)
                return@suspendCancellableCoroutine
            }

            val completionJob =
                mainScope.launch {
                    activeJobs.forEach { it.join() }
                    continuation.resume(Unit)
                }

            continuation.invokeOnCancellation {
                completionJob.cancel()
            }
        }
}
