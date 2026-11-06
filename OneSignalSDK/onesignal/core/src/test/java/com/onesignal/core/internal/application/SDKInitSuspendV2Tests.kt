package com.onesignal.core.internal.application

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.ErrorCode
import com.onesignal.OneSignalUserProfile
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.internal.OneSignalImpV2
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

@RobolectricTest
class SDKInitSuspendV2Tests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    afterAny {
        val context = getApplicationContext<Context>()

        // AGGRESSIVE CLEANUP: Clear ALL SharedPreferences to ensure complete isolation
        val prefs = context.getSharedPreferences("OneSignal", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        // Also clear any other potential SharedPreferences files
        val otherPrefs = context.getSharedPreferences("com.onesignal", Context.MODE_PRIVATE)
        otherPrefs.edit().clear().commit()

        // Wait longer to ensure cleanup is complete
        Thread.sleep(50)
    }

    // ===== INITIALIZATION TESTS =====

    test("initWithContextSuspend with appId returns true") {
        // Given
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        runBlocking {
            // When
            val result = os.initWithContextSuspend(context, "testAppId")

            // Then
            result shouldBe true
            os.isInitialized shouldBe true
        }
    }

    test("initWithContextSuspend with null appId fails when configModel has no appId") {
        // Fresh prefs files: other tests' background inits write the shared store, which made this flaky.
        val context = IsolatedPrefsContext(getApplicationContext())
        val os = OneSignalImpV2()

        runBlocking {
            os.initWithContextSuspend(context, null) shouldBe false
            os.isInitialized shouldBe false
        }
    }

    test("initWithContextSuspend is idempotent") {
        // Given
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        runBlocking {
            // When
            val result1 = os.initWithContextSuspend(context, "testAppId")
            val result2 = os.initWithContextSuspend(context, "testAppId")
            val result3 = os.initWithContextSuspend(context, "testAppId")

            // Then
            result1 shouldBe true
            result2 shouldBe true
            result3 shouldBe true
            os.isInitialized shouldBe true
        }
    }

    // ===== LOGIN TESTS =====

    test("login after initWithContextSuspend switches to the external ID") {
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        runBlocking {
            os.initWithContextSuspend(context, "testAppId") shouldBe true
            os.login("testUser123")
            os.user.externalId shouldBe "testUser123"
        }
    }

    test("login with a JWT after initWithContextSuspend switches to the external ID") {
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        runBlocking {
            os.initWithContextSuspend(context, "testAppId") shouldBe true
            os.login("testUser789", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
            os.user.externalId shouldBe "testUser789"
        }
    }

    test("logout after login returns to a device-scoped user") {
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        runBlocking {
            os.initWithContextSuspend(context, "testAppId") shouldBe true
            os.login("user1")
            os.logout()
            os.user.externalId shouldBe ""
        }
    }

    test("the last of several logins wins") {
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        runBlocking {
            os.initWithContextSuspend(context, "testAppId") shouldBe true
            os.login("user1")
            os.login("user2")
            os.login("user3")
            os.user.externalId shouldBe "user3"
        }
    }

    test("login, logout, login ends on the second user") {
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        runBlocking {
            os.initWithContextSuspend(context, "testAppId") shouldBe true
            os.login("user1")
            os.logout()
            os.login("user2")
            os.user.externalId shouldBe "user2"
        }
    }

    test("suspend login, logout and updateUserJwt are ignored without throwing when initWithContext is never called") {
        val oneSignalImp = OneSignalImpV2()

        runBlocking {
            oneSignalImp.loginSuspend("testUser", null)
            oneSignalImp.logoutSuspend()
            oneSignalImp.updateUserJwtSuspend("testUser", "token")
        }

        oneSignalImp.isInitialized shouldBe false
    }

    test("composite login returns NOT_INITIALIZED when initWithContext is never called") {
        val oneSignalImp = OneSignalImpV2()

        val result = runBlocking { oneSignalImp.login("testUser", OneSignalUserProfile()) }

        result.data shouldBe null
        result.error!!.first.code shouldBe ErrorCode.NOT_INITIALIZED
    }

    test("suspend consent accessors work before initWithContext") {
        val oneSignalImp = OneSignalImpV2()

        runBlocking {
            oneSignalImp.setConsentRequired(true)
            oneSignalImp.setConsentGiven(true)
            oneSignalImp.getConsentRequired() shouldBe true
            oneSignalImp.getConsentGiven() shouldBe true
            oneSignalImp.getDisableGMSMissingPrompt() shouldBe false
        }
    }
})
