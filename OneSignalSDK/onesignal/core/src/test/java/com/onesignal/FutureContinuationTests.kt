package com.onesignal

import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.exceptions.MainThreadException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Robolectric because the main-thread guard reads the real [android.os.Looper].
 *
 * Under Robolectric the spec body itself runs on the main thread, which is what makes the refusal
 * test read as directly as it does — and why every test that expects a value has to go through
 * [offMainThread].
 */
@RobolectricTest
class FutureContinuationTests : FunSpec({

    test("a resumed call hands back its value") {
        val future = Continue.future<String>()

        future.resumeWith(Result.success("os-1"))

        offMainThread { future.get() } shouldBe "os-1"
    }

    test("isDone flips only once the call has completed") {
        val future = Continue.future<String>()

        future.isDone.shouldBeFalse()

        future.resumeWith(Result.success("os-1"))

        future.isDone.shouldBeTrue()
    }

    // Blocking here is the stall the async migration exists to remove, so it is refused outright
    // rather than logged and allowed through.
    test("blocking on the main thread is refused") {
        val future = Continue.future<String>()
        future.resumeWith(Result.success("os-1"))

        val thrown = shouldThrow<MainThreadException> { future.get() }

        thrown.message shouldBe
            "Blocking on a OneSignal call from the main thread is not allowed. " +
            "Call get() from a background thread, or use Continue.callback() to be notified instead."
    }

    // Refused even when the value is already sitting there: a rule that depends on whether the call
    // happened to finish first would throw or not throw depending on timing.
    test("blocking on the main thread is refused even when the value has already arrived") {
        val future = Continue.future<String>()
        future.resumeWith(Result.success("os-1"))
        future.isDone.shouldBeTrue()

        shouldThrow<MainThreadException> { future.get() }
    }

    test("a thrown failure surfaces wrapped, with the original attached as the cause") {
        val boom = IllegalStateException("boom")
        val future = Continue.future<String>()

        future.resumeWith(Result.failure(boom))

        val thrown = shouldThrow<ExecutionException> { offMainThread { future.get() } }
        thrown.cause shouldBe boom
        future.isCancelled.shouldBeFalse()
    }

    // Future specifies cancellation as its own signal rather than an execution failure, which is
    // also what keeps a cancelled call from being mistaken for a failed one.
    test("cancellation surfaces as cancellation rather than as a failure") {
        val future = Continue.future<String>()

        future.resumeWith(Result.failure(CancellationException("parent scope went away")))

        shouldThrow<CancellationException> { offMainThread { future.get() } }
        future.isCancelled.shouldBeTrue()
    }

    test("a call that has not completed times out without disturbing the call") {
        val future = Continue.future<String>()

        shouldThrow<TimeoutException> { offMainThread { future.get(50, TimeUnit.MILLISECONDS) } }

        future.isDone.shouldBeFalse()
        future.resumeWith(Result.success("late"))
        offMainThread { future.get() } shouldBe "late"
    }

    // The caller starts the suspending function directly, so this bridge never has a job to cancel.
    // Reporting that honestly is better than a cancel() that quietly does nothing.
    test("cancel reports that it did not cancel anything") {
        val future = Continue.future<String>()

        future.cancel(true).shouldBeFalse()
        future.isCancelled.shouldBeFalse()
    }

    test("a Unit-returning call completes rather than hanging") {
        val future = Continue.future<Unit>()

        future.resumeWith(Result.success(Unit))

        offMainThread { future.get() } shouldBe Unit
    }
})

/**
 * Runs [block] on a background thread and returns its value, so a test can block on a future the
 * way a real Java caller would. Failures are rethrown on the calling thread so `shouldThrow` still
 * sees the exception the future produced.
 */
private fun <T> offMainThread(block: () -> T): T {
    var result: Result<T>? = null
    val thread = Thread { result = runCatching(block) }
    thread.start()
    thread.join(5_000)
    // Reported explicitly rather than left to fail as a null dereference below, so a hung thread
    // says so instead of surfacing as a confusing NPE.
    check(!thread.isAlive) { "the background thread never finished" }
    return result!!.getOrThrow()
}
