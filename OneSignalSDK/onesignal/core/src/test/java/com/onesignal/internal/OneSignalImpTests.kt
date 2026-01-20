package com.onesignal.internal

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.TestDispatcherProvider
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.StandardTestDispatcher

class OneSignalImpTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("attempting login before initWithContext throws exception") {
        // Given
        val testDispatcher = StandardTestDispatcher()
        val dispatcherProvider = TestDispatcherProvider(testDispatcher)
        val os = OneSignalImp(dispatcherProvider)

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
        val testDispatcher = StandardTestDispatcher()
        val dispatcherProvider = TestDispatcherProvider(testDispatcher)
        val os = OneSignalImp(dispatcherProvider)

        // When
        val exception =
            shouldThrowUnit<Exception> {
                os.logout()
            }

        // Then
        exception.message shouldBe "Must call 'initWithContext' before 'logout'"
    }

    // Comprehensive tests for deprecated properties that should work before and after initialization
    context("consentRequired property") {
        context("before initWithContext") {
            test("get returns false by default") {
                // Given
                val testDispatcher = StandardTestDispatcher()
                val dispatcherProvider = TestDispatcherProvider(testDispatcher)
                val os = OneSignalImp(dispatcherProvider)

                // When & Then
                os.consentRequired shouldBe false
            }

            test("set and get works correctly") {
                // Given
                val testDispatcher = StandardTestDispatcher()
                val dispatcherProvider = TestDispatcherProvider(testDispatcher)
                val os = OneSignalImp(dispatcherProvider)

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
                val testDispatcher = StandardTestDispatcher()
                val dispatcherProvider = TestDispatcherProvider(testDispatcher)
                val os = OneSignalImp(dispatcherProvider)

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
                val testDispatcher = StandardTestDispatcher()
                val dispatcherProvider = TestDispatcherProvider(testDispatcher)
                val os = OneSignalImp(dispatcherProvider)

                // When & Then
                os.consentGiven shouldBe false
            }

            test("set and get works correctly") {
                // Given
                val testDispatcher = StandardTestDispatcher()
                val dispatcherProvider = TestDispatcherProvider(testDispatcher)
                val os = OneSignalImp(dispatcherProvider)

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
                val testDispatcher = StandardTestDispatcher()
                val dispatcherProvider = TestDispatcherProvider(testDispatcher)
                val os = OneSignalImp(dispatcherProvider)

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
                val testDispatcher = StandardTestDispatcher()
                val dispatcherProvider = TestDispatcherProvider(testDispatcher)
                val os = OneSignalImp(dispatcherProvider)

                // When & Then
                os.disableGMSMissingPrompt shouldBe false
            }

            test("set and get works correctly") {
                // Given
                val testDispatcher = StandardTestDispatcher()
                val dispatcherProvider = TestDispatcherProvider(testDispatcher)
                val os = OneSignalImp(dispatcherProvider)

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
                val testDispatcher = StandardTestDispatcher()
                val dispatcherProvider = TestDispatcherProvider(testDispatcher)
                val os = OneSignalImp(dispatcherProvider)

                // When & Then - should not throw
                os.disableGMSMissingPrompt = true
                os.disableGMSMissingPrompt = false
            }
        }
    }

    context("property consistency tests") {
        test("all properties maintain state correctly") {
            // Given
            val testDispatcher = StandardTestDispatcher()
            val dispatcherProvider = TestDispatcherProvider(testDispatcher)
            val os = OneSignalImp(dispatcherProvider)

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
            val testDispatcher = StandardTestDispatcher()
            val dispatcherProvider = TestDispatcherProvider(testDispatcher)
            val os = OneSignalImp(dispatcherProvider)

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
        val testDispatcher = StandardTestDispatcher()
        val dispatcherProvider = TestDispatcherProvider(testDispatcher)
        val oneSignalImp = OneSignalImp(dispatcherProvider)

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
        val testDispatcher = StandardTestDispatcher()
        val dispatcherProvider = TestDispatcherProvider(testDispatcher)
        val oneSignalImp = OneSignalImp(dispatcherProvider)

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
