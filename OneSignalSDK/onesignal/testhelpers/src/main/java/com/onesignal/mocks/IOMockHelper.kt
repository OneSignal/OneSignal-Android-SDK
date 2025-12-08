package com.onesignal.mocks

import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.suspendifyOnIO
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.listeners.BeforeTestListener
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test helper that makes OneSignal's async threading behavior deterministic in unit tests.
 * Can be helpful to speed up unit tests by replacing all delay(x) or Thread.sleep(x).
 *
 * In production, `suspendifyOnIO`, `launchOnIO`, and `launchOnDefault` launch work on
 * background threads and return immediately. This causes tests to require arbitrary delays
 * (e.g., delay(50)) to wait for async work to finish.
 *
 * This helper avoids that by:
 *  - Mocking `suspendifyOnIO`, `launchOnIO`, and `launchOnDefault` so their blocks run immediately
 *  - Completing a `CompletableDeferred` when the async block finishes
 *  - Providing `awaitIO()` so tests can explicitly wait for all async work without sleeps
 *
 * Usage example in a Kotest spec:
 *   class InAppMessagesManagerTests : FunSpec({
 *
 *     // register to access awaitIO()
 *     listener(IOMockHelper)
 *   ...
 *
 *     test("xyz") {
 *         iamManager.start()    // start() calls suspendOnIO or launchOnDefault
 *         awaitIO()    // wait for background work deterministically
 *         ...
 *     }
 */
object IOMockHelper : BeforeSpecListener, AfterSpecListener, BeforeTestListener, TestListener {

    private const val THREADUTILS_PATH = "com.onesignal.common.threading.ThreadUtilsKt"

    // How many async blocks (suspendifyOnIO, launchOnIO, launchOnDefault) are currently running
    private val pendingIo = AtomicInteger(0)

    // Completed when all in-flight async blocks for the current "wave" are done
    @Volatile
    private var ioWaiter: CompletableDeferred<Unit> = CompletableDeferred()

    /**
     * Wait for suspendifyOnIO, launchOnIO, and launchOnDefault work to finish.
     * Can be called multiple times in a test.
     *  1. If multiple async tasks are added before the first task finishes, the waiter will wait until ALL tasks are finished
     *  2. If async work is triggered after an awaitIO() has already returned, just call awaitIO() again to wait for the new work.
     */
    suspend fun awaitIO(timeoutMs: Long = 5_000) {
        // Nothing to wait for in this case
        if (pendingIo.get() == 0) return

        withTimeout(timeoutMs) {
            ioWaiter.await()
        }
    }

    override suspend fun beforeSpec(spec: Spec) {
        // ThreadUtilsKt = file that contains suspendifyOnIO
        mockkStatic(THREADUTILS_PATH)
        // OneSignalDispatchers = object that contains launchOnIO and launchOnDefault
        mockkObject(OneSignalDispatchers)

        // Helper function to track async work (suspendifyOnIO, launchOnIO, launchOnDefault)
        // Note: We use Dispatchers.Unconfined to execute immediately and deterministically
        // instead of suspendifyWithCompletion to avoid circular dependency
        // (suspendifyWithCompletion calls OneSignalDispatchers.launchOnIO which we're mocking)
        fun trackAsyncWork(block: suspend () -> Unit) {
            // New async wave: if we are going from 0 -> 1, create a new waiter
            val previous = pendingIo.getAndIncrement()
            if (previous == 0) {
                ioWaiter = CompletableDeferred()
            }

            // Execute the block using Unconfined dispatcher to run immediately and deterministically
            // This makes tests deterministic and avoids the need for delays
            CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).launch {
                try {
                    block()
                } catch (e: Exception) {
                    // Log but don't throw - let the test handle exceptions
                } finally {
                    // When each block finishes, decrement; if all done, complete waiter
                    if (pendingIo.decrementAndGet() == 0) {
                        if (!ioWaiter.isCompleted) {
                            ioWaiter.complete(Unit)
                        }
                    }
                }
            }
        }

        every { suspendifyOnIO(any<suspend () -> Unit>()) } answers {
            val block = firstArg<suspend () -> Unit>()
            trackAsyncWork(block)
        }

        every { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) } answers {
            val block = firstArg<suspend () -> Unit>()
            trackAsyncWork(block)
            // Return a mock Job (launchOnIO returns a Job)
            mockk<Job>(relaxed = true)
        }

        every { OneSignalDispatchers.launchOnDefault(any<suspend () -> Unit>()) } answers {
            val block = firstArg<suspend () -> Unit>()
            trackAsyncWork(block)
            // Return a mock Job (launchOnDefault returns a Job)
            mockk<Job>(relaxed = true)
        }
    }

    override suspend fun beforeTest(testCase: TestCase) {
        // Fresh waiter for each test
        pendingIo.set(0)
        ioWaiter = CompletableDeferred()
    }

    override suspend fun afterSpec(spec: Spec) {
        unmockkStatic(THREADUTILS_PATH)
        unmockkObject(OneSignalDispatchers)
    }
}
