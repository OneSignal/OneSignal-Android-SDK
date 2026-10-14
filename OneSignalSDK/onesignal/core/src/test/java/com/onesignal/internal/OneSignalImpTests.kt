package com.onesignal.internal

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OneSignalImpTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
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

    test("attempting login before initWithContext is ignored without throwing") {
        val os = OneSignalImp()
        // NONE: otherwise Logging.error calls the unmocked android.util.Log.
        Logging.logLevel = LogLevel.NONE

        os.login("login-id")
        os.login("")

        os.isInitialized shouldBe false
    }

    test("login with empty externalId after init does not switch user") {
        val os = OneSignalImp()
        markInitialized(os)
        // NONE: otherwise Logging.error calls the unmocked android.util.Log.
        Logging.logLevel = LogLevel.NONE

        os.login("")
    }

    test("login with a null byte in externalId after init does not switch user") {
        val os = OneSignalImp()
        markInitialized(os)
        Logging.logLevel = LogLevel.NONE

        os.login("\u0000: 1")
    }

    test("updateUserJwt with empty externalId or token after init does not store the token") {
        val os = OneSignalImp()
        markInitialized(os)
        Logging.logLevel = LogLevel.NONE

        os.updateUserJwt("", "token")
        os.updateUserJwt("user", "")
    }

    test("attempting logout before initWithContext is ignored without throwing") {
        val os = OneSignalImp()
        Logging.logLevel = LogLevel.NONE

        os.logout()

        os.isInitialized shouldBe false
    }

    test("isReady returns synchronously without dispatching when initState is SUCCESS") {
        val os = OneSignalImp()
        markInitialized(os)

        val isReady = OneSignalImp::class.java.getDeclaredMethod("isReady", String::class.java)
        isReady.isAccessible = true
        isReady.invoke(os, "test") shouldBe true
    }

    test("isReady returns false without throwing at NOT_STARTED and FAILED") {
        val os = OneSignalImp()
        Logging.logLevel = LogLevel.NONE
        val isReady = OneSignalImp::class.java.getDeclaredMethod("isReady", String::class.java)
        isReady.isAccessible = true

        isReady.invoke(os, "test") shouldBe false

        setPrivateField(os, "initState", InitState.FAILED)
        isReady.invoke(os, "test") shouldBe false
    }

    test("blockingGet returns synchronously without dispatching when initState is SUCCESS") {
        val os = OneSignalImp()
        markInitialized(os)

        val blockingGet = OneSignalImp::class.java.getDeclaredMethod("blockingGet", Function0::class.java)
        blockingGet.isAccessible = true

        val getter = { "value" }
        blockingGet.invoke(os, getter) shouldBe "value"
    }

    test("isReady at IN_PROGRESS awaits the completion signal on the caller's event loop") {
        // The IN_PROGRESS wait uses a dispatcher-less runBlocking so it resumes on the caller's
        // own event loop off the completion signal alone, rather than depending on a free worker
        // in a (possibly saturated) IO pool (#1163).
        val os = OneSignalImp()
        setPrivateField(os, "initState", InitState.IN_PROGRESS)
        val deferred = CompletableDeferred<Unit>()
        setPrivateField(os, "suspendCompletion", deferred)

        val isReady = OneSignalImp::class.java.getDeclaredMethod("isReady", String::class.java)
        isReady.isAccessible = true

        val failure = arrayOfNulls<Throwable>(1)
        val finished = CountDownLatch(1)
        Thread {
            try {
                isReady.invoke(os, "test")
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
})
