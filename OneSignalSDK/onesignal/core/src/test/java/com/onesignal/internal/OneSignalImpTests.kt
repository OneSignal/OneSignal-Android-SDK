package com.onesignal.internal

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class OneSignalImpTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
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

    // consentRequired probably should have thrown like the other OneSignal methods in 5.0.0,
    // but we can't make a breaking change to an existing API.
    context("consentRequired") {
        context("before initWithContext") {
            test("set should not throw") {
                // Given
                val os = OneSignalImp()
                // When
                os.consentRequired = false
                os.consentRequired = true
                // Then
                // Test fails if the above throws
            }
            test("get should not throw") {
                // Given
                val os = OneSignalImp()
                // When
                println(os.consentRequired)
                // Then
                // Test fails if the above throws
            }
        }
    }

    // consentGiven probably should have thrown like the other OneSignal methods in 5.0.0,
    // but we can't make a breaking change to an existing API.
    context("consentGiven") {
        context("before initWithContext") {
            test("set should not throw") {
                // Given
                val os = OneSignalImp()
                // When
                os.consentGiven = true
                os.consentGiven = false
                // Then
                // Test fails if the above throws
            }
            test("get should not throw") {
                // Given
                val os = OneSignalImp()
                // When
                println(os.consentGiven)
                // Then
                // Test fails if the above throws
            }
        }
    }
})
