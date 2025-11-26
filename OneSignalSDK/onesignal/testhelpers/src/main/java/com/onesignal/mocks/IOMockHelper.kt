package com.onesignal.mocks

import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.common.threading.suspendifyWithCompletion
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.listeners.BeforeTestListener
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test helper that makes OneSignal’s `suspendifyOnIO` behavior deterministic in unit tests.
 * Can be helpful to speed up unit tests by replacing all delay(x) or Thread.sleep(x).
 *
 * In production, `suspendifyOnIO` launches work on background threads and returns immediately.
 * This causes tests to require arbitrary delays (e.g., delay(50)) to wait for async work to finish.
 *
 * This helper avoids that by:
 *  - Replacing Dispatchers.Main with a test dispatcher
 *  - Mocking `suspendifyOnIO` so its block runs immediately
 *  - Completing a `CompletableDeferred` when the async block finishes
 *  - Providing `awaitIO()` so tests can explicitly wait for all IO work without sleeps
 *
 * Usage example in a Kotest spec:
 *   class InAppMessagesManagerTests : FunSpec({
 *
 *     // register to access awaitIO()
 *     listener(IOMockHelper)
 *   ...
 *
 *     test("xyz") {
 *         iamManager.start()    // start() calls suspendOnIO
 *         awaitIO()    // wait for background work deterministically
 *         ...
 *     }
 */
object IOMockHelper : BeforeSpecListener, AfterSpecListener, BeforeTestListener, TestListener {

    private const val THREADUTILS_PATH = "com.onesignal.common.threading.ThreadUtilsKt"

    // How many IO blocks are currently running
    private val pendingIo = AtomicInteger(0)

    // Completed when all in-flight IO blocks for the current "wave" are done
    @Volatile
    private var ioWaiter: CompletableDeferred<Unit> = CompletableDeferred()

    /**
     * Wait for suspendifyOnIO work to finish.
     * Can be called multiple times in a test.
     *  1. If multiple IO tasks are added before the first task finishes, the waiter will wait until ALL tasks are finished
     *  2. If async work is triggered after an awaitIO() has already returned, just call awaitIO() again to wait for the new work.
     *
     *  *** NOTE ABOUT COVERAGE:
     *  * This helper intentionally mocks *only* the top-level `suspendifyOnIO(block)` function.
     *    It does NOT intercept every threading entry point defined in ThreadUtils.kt or
     *    OneSignalDispatchers — e.g. `suspendifyWithCompletion`, `suspendifyOnDefault`,
     *    `launchOnIO`, and `launchOnDefault` will continue to run using the real dispatcher
     *    behavior.
     *
     *  * This design keeps the helper focused on stabilizing existing tests that specifically
     *    depend on `suspendifyOnIO`, without altering unrelated threading paths across the SDK.
     *
     *  * If future tests rely on other threading helpers (e.g., direct calls to
     *    `suspendifyWithCompletion` or `launchOnIO`), this helper can be extended, or a separate
     *    test helper can be introduced to cover those cases. For now, this keeps the
     *    interception surface minimal and avoids unintentionally changing more concurrency
     *    behavior than necessary.
     */
    suspend fun awaitIO() {
        // Nothing to wait for in this case
        if (pendingIo.get() == 0) return

        ioWaiter.await()
    }

    override suspend fun beforeSpec(spec: Spec) {
        // ThreadUtilsKt = file that contains suspendifyOnIO
        mockkStatic(THREADUTILS_PATH)

        every {
            suspendifyWithCompletion(
                useIO = any(),
                block = any<suspend () -> Unit>(),
                onComplete = any()
            )
        } answers { callOriginal() }

        every { suspendifyOnIO(any<suspend () -> Unit>()) } answers {
            val block = firstArg<suspend () -> Unit>()

            // New IO wave: if we are going from 0 -> 1, create a new waiter
            val previous = pendingIo.getAndIncrement()
            if (previous == 0) {
                ioWaiter = CompletableDeferred()
            }

            suspendifyWithCompletion(
                useIO = true,
                block = block,
                onComplete = {
                    // When each block finishes, decrement; if all done, complete waiter
                    if (pendingIo.decrementAndGet() == 0) {
                        if (!ioWaiter.isCompleted) {
                            ioWaiter.complete(Unit)
                        }
                    }
                },
            )
        }
    }

    override suspend fun beforeTest(testCase: TestCase) {
        // Fresh waiter for each test
        pendingIo.set(0)
        ioWaiter = CompletableDeferred()
    }

    override suspend fun afterSpec(spec: Spec) {
        unmockkStatic(THREADUTILS_PATH)
    }
}
