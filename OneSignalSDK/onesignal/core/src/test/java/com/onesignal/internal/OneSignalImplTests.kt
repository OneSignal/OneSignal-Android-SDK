package com.onesignal.internal

import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class OneSignalImplTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("attempting login before initWithContext throws exception") {
        // Given
        val os = OneSignalImp()

        // When
        val exception = shouldThrowUnit<Exception> {
            os.login("login-id")
        }

        // Then
        exception.message shouldBe "Must call 'initWithContext' before 'login'"
    }

    test("attempting logout before initWithContext throws exception") {
        // Given
        val os = OneSignalImp()

        // When
        val exception = shouldThrowUnit<Exception> {
            os.logout()
        }

        // Then
        exception.message shouldBe "Must call 'initWithContext' before 'logout'"
    }
})
