package com.onesignal.common.threading

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One caller runs [block]. Later callers wait for that result instead of running it again.
 */
class InFlightResult<T> {
    private val gate = Mutex()
    private var current: CompletableDeferred<T>? = null

    /** One caller runs [block]. Later callers wait for that result. */
    suspend fun share(
        fallback: T,
        block: suspend () -> T,
    ): T {
        val (deferred, owner) =
            gate.withLock {
                val existing = current
                if (existing != null) {
                    existing to false
                } else {
                    CompletableDeferred<T>().also { current = it } to true
                }
            }
        if (!owner) {
            return deferred.await()
        }
        return try {
            val result = block()
            deferred.complete(result)
            result
        } finally {
            // A cancelled owner cannot wait on a held lock, which would leave [current] stuck.
            withContext(NonCancellable) {
                if (!deferred.isCompleted) {
                    deferred.complete(fallback)
                }
                gate.withLock {
                    if (current === deferred) {
                        current = null
                    }
                }
            }
        }
    }
}
