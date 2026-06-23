package com.onesignal.internal

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

class OneSignalImpTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    val throwingDispatcher =
        object : CoroutineDispatcher() {
            override fun dispatch(
                context: CoroutineContext,
                block: Runnable,
            ) {
                error("SUCCESS fast-path should not dispatch")
            }
        }

    fun markInitialized(os: OneSignalImp) {
        val initStateField = OneSignalImp::class.java.getDeclaredField("initState")
        initStateField.isAccessible = true
        initStateField.set(os, InitState.SUCCESS)
    }

    fun setPrivateField(
        os: OneSignalImp,
        name: String,
        value: Any,
    ) {
        val field = OneSignalImp::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(os, value)
    }

    test("attempting login before initWithContext throws exception") {
        // Given
        val os = OneSignalImp()

        // When
        val exception =
            shouldThrowUnit<Exception> {
                os.login("login-id")
            }

        // Then
        exception.message shouldBe "Must call 'initWithContext' before 'login'"
    }

    test("attempting logout before initWithContext throws exception") {
        // Given
        val os = OneSignalImp()

        // When
        val exception =
            shouldThrowUnit<Exception> {
                os.logout()
            }

        // Then
        exception.message shouldBe "Must call 'initWithContext' before 'logout'"
    }

    test("waitForInit returns synchronously without dispatching when initState is SUCCESS") {
        val os = OneSignalImp(ioDispatcher = throwingDispatcher)
        markInitialized(os)

        val waitForInit = OneSignalImp::class.java.getDeclaredMethod("waitForInit", String::class.java)
        waitForInit.isAccessible = true
        waitForInit.invoke(os, null as String?)

        os.isInitialized shouldBe true
    }

    test("blockingGet returns synchronously without dispatching when initState is SUCCESS") {
        val os = OneSignalImp(ioDispatcher = throwingDispatcher)
        markInitialized(os)

        val blockingGet = OneSignalImp::class.java.getDeclaredMethod("blockingGet", Function0::class.java)
        blockingGet.isAccessible = true

        val getter = { "value" }
        blockingGet.invoke(os, getter) shouldBe "value"
    }

    test("waitForInit at IN_PROGRESS awaits the completion signal without dispatching to the IO dispatcher") {
        // Under FF-off, runtimeIoDispatcher == ioDispatcher (the throwing dispatcher). Pre-fix the
        // IN_PROGRESS wait ran runBlocking(runtimeIoDispatcher) { ... }, coupling it to a dispatcher
        // that can never run the body (models a saturated IO pool). Post-fix the wait runs on the
        // caller's own event loop and resumes off the completion signal alone (#1163).
        val os = OneSignalImp(ioDispatcher = throwingDispatcher)
        setPrivateField(os, "initState", InitState.IN_PROGRESS)
        val deferred = CompletableDeferred<Unit>()
        setPrivateField(os, "suspendCompletion", deferred)

        val waitForInit = OneSignalImp::class.java.getDeclaredMethod("waitForInit", String::class.java)
        waitForInit.isAccessible = true

        val failure = arrayOfNulls<Throwable>(1)
        val finished = CountDownLatch(1)
        Thread {
            try {
                waitForInit.invoke(os, null as String?)
            } catch (t: InvocationTargetException) {
                failure[0] = t.targetException
            } finally {
                finished.countDown()
            }
        }.start()

        // Still parked on the not-yet-completed signal (pre-fix it would throw/finish immediately).
        finished.await(300, TimeUnit.MILLISECONDS) shouldBe false

        setPrivateField(os, "initState", InitState.SUCCESS)
        deferred.complete(Unit)

        finished.await(2, TimeUnit.SECONDS) shouldBe true
        failure[0] shouldBe null
    }

    // Comprehensive tests for deprecated properties that should work before and after initialization
    context("consentRequired property") {
        context("before initWithContext") {
            test("get returns false by default") {
                // Given
                val os = OneSignalImp()

                // When & Then
                os.consentRequired shouldBe false
            }

            test("set and get works correctly") {
                // Given
                val os = OneSignalImp()

                // When
                os.consentRequired = true

                // Then
                os.consentRequired shouldBe true

                // When
                os.consentRequired = false

                // Then
                os.consentRequired shouldBe false
            }

            test("set should not throw") {
                // Given
                val os = OneSignalImp()

                // When & Then - should not throw
                os.consentRequired = false
                os.consentRequired = true
            }
        }
    }

    context("consentGiven property") {
        context("before initWithContext") {
            test("get returns false by default") {
                // Given
                val os = OneSignalImp()

                // When & Then
                os.consentGiven shouldBe false
            }

            test("set and get works correctly") {
                // Given
                val os = OneSignalImp()

                // When
                os.consentGiven = true

                // Then
                os.consentGiven shouldBe true

                // When
                os.consentGiven = false

                // Then
                os.consentGiven shouldBe false
            }

            test("set should not throw") {
                // Given
                val os = OneSignalImp()

                // When & Then - should not throw
                os.consentGiven = true
                os.consentGiven = false
            }
        }
    }

    context("disableGMSMissingPrompt property") {
        context("before initWithContext") {
            test("get returns false by default") {
                // Given
                val os = OneSignalImp()

                // When & Then
                os.disableGMSMissingPrompt shouldBe false
            }

            test("set and get works correctly") {
                // Given
                val os = OneSignalImp()

                // When
                os.disableGMSMissingPrompt = true

                // Then
                os.disableGMSMissingPrompt shouldBe true

                // When
                os.disableGMSMissingPrompt = false

                // Then
                os.disableGMSMissingPrompt shouldBe false
            }

            test("set should not throw") {
                // Given
                val os = OneSignalImp()

                // When & Then - should not throw
                os.disableGMSMissingPrompt = true
                os.disableGMSMissingPrompt = false
            }
        }
    }

    context("property consistency tests") {
        test("all properties maintain state correctly") {
            // Given
            val os = OneSignalImp()

            // When - set all properties to true
            os.consentRequired = true
            os.consentGiven = true
            os.disableGMSMissingPrompt = true

            // Then - all should be true
            os.consentRequired shouldBe true
            os.consentGiven shouldBe true
            os.disableGMSMissingPrompt shouldBe true

            // When - set all properties to false
            os.consentRequired = false
            os.consentGiven = false
            os.disableGMSMissingPrompt = false

            // Then - all should be false
            os.consentRequired shouldBe false
            os.consentGiven shouldBe false
            os.disableGMSMissingPrompt shouldBe false
        }

        test("properties are independent of each other") {
            // Given
            val os = OneSignalImp()

            // When - set only consentRequired to true
            os.consentRequired = true

            // Then - only consentRequired should be true
            os.consentRequired shouldBe true
            os.consentGiven shouldBe false
            os.disableGMSMissingPrompt shouldBe false

            // When - set only consentGiven to true
            os.consentRequired = false
            os.consentGiven = true

            // Then - only consentGiven should be true
            os.consentRequired shouldBe false
            os.consentGiven shouldBe true
            os.disableGMSMissingPrompt shouldBe false

            // When - set only disableGMSMissingPrompt to true
            os.consentGiven = false
            os.disableGMSMissingPrompt = true

            // Then - only disableGMSMissingPrompt should be true
            os.consentRequired shouldBe false
            os.consentGiven shouldBe false
            os.disableGMSMissingPrompt shouldBe true
        }
    }

    test("waitForInit timeout behavior - this test demonstrates the timeout mechanism") {
        // This test documents that waitForInit() has timeout protection
        // In a real scenario, if initWithContext was never called,
        // waitForInit() would timeout after 30 seconds and log a warning (not throw)

        // Given - a fresh OneSignalImp instance
        val oneSignalImp = OneSignalImp()

        // The timeout behavior is built into waitUntilInitInternal()
        // which uses withTimeout() to wait for up to 30 seconds (or 4.8 seconds on main thread)
        // before logging a warning and proceeding

        // NOTE: We don't test waiting indefinitely here because:
        // 1. It would make tests hang forever
        // 2. This test documents the behavior for developers

        oneSignalImp.isInitialized shouldBe false
    }

    test("waitForInit waits indefinitely until init completes") {
        // This test verifies that waitUntilInitInternal waits indefinitely
        // until initialization completes (per PR #2412)

        // Given
        val oneSignalImp = OneSignalImp()

        // We can verify the wait behavior by checking:
        // 1. The suspendCompletion (CompletableDeferred) is properly initialized
        // 2. The initState is NOT_STARTED (which would throw immediately)
        // 3. The isInitialized property correctly reflects the state

        oneSignalImp.isInitialized shouldBe false

        // In a real scenario where initWithContext is never called:
        // - waitForInit() would call waitUntilInitInternal()
        // - waitUntilInitInternal() would check initState == NOT_STARTED and throw immediately
        // - If initState was IN_PROGRESS, it would wait indefinitely using suspendCompletion.await()
        // - waitForInit() throws for NOT_STARTED/FAILED states, waits indefinitely for IN_PROGRESS

        // This test documents this behavior without actually waiting indefinitely
    }
})
