package com.onesignal.internal

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.core.internal.preferences.impl.PreferencesService
// import com.onesignal.notifications.NotificationsModule
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.mocks.MockHelper
import com.onesignal.mocks.MockPreferencesService
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

@RobolectricTest
class OneSignalImpTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.VERBOSE
    }

//    test("attempting login before initWithContext throws exception") {
//        // Given
//        val os = OneSignalImp()
//
//        // When
//        val exception =
//            shouldThrowUnit<Exception> {
//                os.login("login-id")
//            }
//
//        // Then
//        exception.message shouldBe "Must call 'initWithContext' before 'login'"
//    }
//
//    test("attempting logout before initWithContext throws exception") {
//        // Given
//        val os = OneSignalImp()
//
//        // When
//        val exception =
//            shouldThrowUnit<Exception> {
//                os.logout()
//            }
//
//        // Then
//        exception.message shouldBe "Must call 'initWithContext' before 'logout'"
//    }
//
//    // consentRequired probably should have thrown like the other OneSignal methods in 5.0.0,
//    // but we can't make a breaking change to an existing API.
//    context("consentRequired") {
//        context("before initWithContext") {
//            test("set should not throw") {
//                // Given
//                val os = OneSignalImp()
//                // When
//                os.consentRequired = false
//                os.consentRequired = true
//                // Then
//                // Test fails if the above throws
//            }
//            test("get should not throw") {
//                // Given
//                val os = OneSignalImp()
//                // When
//                println(os.consentRequired)
//                // Then
//                // Test fails if the above throws
//            }
//        }
//    }
//
//    // consentGiven probably should have thrown like the other OneSignal methods in 5.0.0,
//    // but we can't make a breaking change to an existing API.
//    context("consentGiven") {
//        context("before initWithContext") {
//            test("set should not throw") {
//                // Given
//                val os = OneSignalImp()
//                // When
//                os.consentGiven = true
//                os.consentGiven = false
//                // Then
//                // Test fails if the above throws
//            }
//            test("get should not throw") {
//                // Given
//                val os = OneSignalImp()
//                // When
//                println(os.consentGiven)
//                // Then
//                // Test fails if the above throws
//            }
//        }
//    }
    test("initWithContext called without appId has cached legacy appId should initialize") {
        val preferencesService = PreferencesService(AndroidMockHelper.applicationService(), MockHelper.time(1000))

        preferencesService.saveString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID, "foo")

        val os = OneSignalImp()
        println("❌ BEFORE isInitialized: ${os.isInitialized}")
        val context = ApplicationProvider.getApplicationContext<Context>()
        os.initWithContext(context, null)
        println("❌ AFTER isInitialized: ${os.isInitialized}")


    }

//    test("initWithContext called without appId does not have cached legacy appId should return early") {
//        // Given
//        // When
//        // Then
//    }
})
