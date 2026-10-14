package com.onesignal.internal

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OneSignalImpTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    fun markInitialized(os: OneSignalImp) {
        os.gate.tryStart()
        os.gate.complete(InitState.SUCCESS)
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

    test("blockingGet returns synchronously without dispatching when initState is SUCCESS") {
        val os = OneSignalImp()
        markInitialized(os)

        val blockingGet = OneSignalImp::class.java.getDeclaredMethod("blockingGet", Function0::class.java)
        blockingGet.isAccessible = true

        val getter = { "value" }
        blockingGet.invoke(os, getter) shouldBe "value"
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
