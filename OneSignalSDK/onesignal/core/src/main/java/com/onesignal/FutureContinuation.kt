package com.onesignal

import com.onesignal.common.AndroidUtils
import com.onesignal.common.exceptions.MainThreadException
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

/**
 * A [Continuation] that is also a [Future], letting Java call a suspending function and collect the
 * answer later:
 *
 * ```java
 * FutureContinuation<Boolean> consent = Continue.future();
 * OneSignal.getConsentGivenSuspend(consent);
 *
 * // ...on any thread that is not the main thread:
 * boolean given = consent.get();
 * ```
 *
 * Obtain one from [Continue.future]. Each instance backs a single call: it can be resumed once, so
 * a second call needs a second instance.
 *
 * The value is whatever the suspending function returns, handed back unchanged. This bridge
 * deliberately does not wrap it in a result type of its own — a suspending function that reports
 * failure by returning something is left to say so in its own vocabulary, and one that reports
 * failure by throwing surfaces here the way [Future] already specifies.
 *
 * What [get] guards is the blocking wait, not the call. A suspending function runs on the thread
 * that started it until it first suspends, so invoking one from the main thread still does that much
 * work there — no continuation can change that. The SDK's suspending APIs change dispatchers almost
 * immediately, so in practice the work leaves the main thread right away.
 */
class FutureContinuation<R> internal constructor() : Continuation<R>, Future<R> {
    /**
     * Fixed at [Dispatchers.Unconfined], so the call resumes on whichever thread finished the work.
     * Resuming records a value and releases a latch, so there is no user code here to place on a
     * particular thread — dispatching elsewhere would only add latency, and dispatching to a busy
     * main thread would add an unbounded amount of it.
     */
    override val context: CoroutineContext
        get() = Dispatchers.Unconfined

    private val completed = CountDownLatch(1)

    @Volatile
    private var value: R? = null

    @Volatile
    private var failure: Throwable? = null

    override fun resumeWith(result: Result<R>) {
        failure = result.exceptionOrNull()
        if (failure == null) {
            value = result.getOrNull()
        }
        completed.countDown()
    }

    /**
     * Blocks until the call completes and returns its value.
     *
     * @throws MainThreadException if called on the main thread. Blocking there is the stall this
     * bridge exists to avoid, so it is refused rather than logged. Use [Continue.callback] when the
     * answer is needed on the main thread.
     * @throws ExecutionException if the call threw. The original throwable is the [Throwable.cause].
     * @throws CancellationException if the call was cancelled.
     */
    override fun get(): R {
        refuseMainThread()
        completed.await()
        return valueOrThrow()
    }

    /**
     * Blocks for at most [timeout] and returns the call's value.
     *
     * Throws as [get] does, plus [TimeoutException] if the call had not completed in time. A
     * timeout leaves the underlying call running — this bridge has no handle on it, see [cancel].
     */
    override fun get(
        timeout: Long,
        unit: TimeUnit,
    ): R {
        refuseMainThread()
        if (!completed.await(timeout, unit)) {
            throw TimeoutException("OneSignal call did not complete within $timeout ${unit.name.lowercase()}.")
        }
        return valueOrThrow()
    }

    override fun isDone(): Boolean = completed.count == 0L

    override fun isCancelled(): Boolean = isDone && failure is CancellationException

    /**
     * Always returns `false`: this continuation is handed to a suspending function that the caller
     * started directly, so there is no job here to cancel. Cancellation that happens upstream is
     * still reported — it arrives as a [CancellationException] and surfaces through [get] and
     * [isCancelled].
     */
    override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

    private fun valueOrThrow(): R {
        when (val thrown = failure) {
            null -> {
                @Suppress("UNCHECKED_CAST")
                return value as R
            }
            // Future specifies CancellationException directly and everything else wrapped, which
            // also keeps a cancelled call from being mistaken for a failed one.
            is CancellationException -> throw thrown
            else -> throw ExecutionException(thrown)
        }
    }

    // Refused whether or not the value has already arrived. Allowing the already-complete case
    // would make the same line throw or not depending on timing, which is a worse contract than a
    // rule that always holds.
    private fun refuseMainThread() {
        if (AndroidUtils.isRunningOnMainThread()) {
            throw MainThreadException(
                "Blocking on a OneSignal call from the main thread is not allowed. " +
                    "Call get() from a background thread, or use Continue.callback() to be notified instead.",
            )
        }
    }
}
