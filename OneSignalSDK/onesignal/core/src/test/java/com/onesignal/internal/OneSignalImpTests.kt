package com.onesignal.internal

import android.content.Context
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk

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

    test("When identity verification is on and no user is created, calling initWithContext will create a new user") {
        // Given
        val os = OneSignalImp()
        val appId = "tempAppId"
        val context = mockk<Context>()

        // When
        os.initWithContext(context, appId)
    }
})
