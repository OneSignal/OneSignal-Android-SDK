package com.onesignal.mocks

import com.onesignal.common.threading.CoroutineDispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher

/**
 * Test implementation of [CoroutineDispatcherProvider] for unit tests.
 * Uses a [TestDispatcher] for deterministic testing.
 *
 * Usage in tests:
 * ```
 * test("my test") {
 *     val testDispatcher = StandardTestDispatcher()
 *     val dispatcherProvider = TestDispatcherProvider(testDispatcher)
 *
 *     runTest(testDispatcher.scheduler) {
 *         val service = MyService(dispatcherProvider)
 *         service.doWork()
 *
 *         // Option 1: Advance until all pending coroutines complete
 *         advanceUntilIdle()
 *
 *         // Option 2: Advance virtual time by a specific amount (e.g., 100ms)
 *         // advanceTimeBy(100)
 *
 *         // Option 3: Run only coroutines scheduled at current time
 *         // runCurrent()
 *
 *         // Make assertions
 *     }
 * }
 * ```
 *
 * Methods to control execution:
 * - [advanceUntilIdle()] - Runs all pending coroutines until there's nothing left to execute
 * - [advanceTimeBy(delayTime)] - Advances virtual time by the specified amount and runs
 *   coroutines scheduled for that time period
 * - [runCurrent()] - Runs only the coroutines that are scheduled to run at the current
 *   virtual time (doesn't advance time)
 * - [currentTime] - Property to check the current virtual time
 */
class TestDispatcherProvider(
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : CoroutineDispatcherProvider {
    override val io: CoroutineDispatcher = testDispatcher
    override val default: CoroutineDispatcher = testDispatcher

    private val scope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + testDispatcher)
    }

    override fun launchOnIO(block: suspend () -> Unit): Job {
        return scope.launch { block() }
    }

    override fun launchOnDefault(block: suspend () -> Unit): Job {
        return scope.launch { block() }
    }
}
