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
import kotlinx.coroutines.runBlocking

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
 * Usage in a Kotest spec:
 *
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

    private var ioWaiter: CompletableDeferred<Unit> = CompletableDeferred()

    /**
     * Wait for the current suspendifyOnIO work to finish.
     * Can be called from tests instead of delay/Thread.sleep.
     */
    fun awaitIO() {
        if (!ioWaiter.isCompleted) {
            runBlocking {
                ioWaiter.await()
            }
        }
        ioWaiter = CompletableDeferred()
    }

    override suspend fun beforeSpec(spec: Spec) {
        // ThreadUtilsKt = file that contains suspendifyOnIO
        mockkStatic("com.onesignal.common.threading.ThreadUtilsKt")

        every { suspendifyOnIO(any<suspend () -> Unit>()) } answers {
            val block = firstArg<suspend () -> Unit>()
            suspendifyWithCompletion(
                useIO = true,
                block = block,
                onComplete = { ioWaiter.complete(Unit) },
            )
        }
    }

    override suspend fun beforeTest(testCase: TestCase) {
        // fresh waiter for each test
        ioWaiter = CompletableDeferred()
    }

    override suspend fun afterSpec(spec: Spec) {
        unmockkStatic("com.onesignal.common.threading.ThreadUtilsKt")
    }
}
