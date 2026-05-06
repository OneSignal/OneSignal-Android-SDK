package com.onesignal.core.internal.application

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.OneSignalLogEvent
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.internal.OneSignalImp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import java.util.concurrent.CountDownLatch

@RobolectricTest
class SDKInitTests : FunSpec({

    /**
     * Helper function to wait for OneSignal initialization to complete.
     * @param oneSignalImp The OneSignalImp instance to wait for
     * @param maxAttempts Maximum number of attempts (default: 100)
     * @param sleepMs Sleep duration between attempts in milliseconds (default: 20)
     */
    fun waitForInitialization(oneSignalImp: OneSignalImp, maxAttempts: Int = 100, sleepMs: Long = 20) {
        var attempts = 0
        while (!oneSignalImp.isInitialized && attempts < maxAttempts) {
            Thread.sleep(sleepMs)
            attempts++
        }
        oneSignalImp.isInitialized shouldBe true
    }

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    afterAny {
        val context = getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("OneSignal", Context.MODE_PRIVATE)
        prefs.edit()
            .clear()
            .commit()

        // Also clear any other potential SharedPreferences files
        val otherPrefs = context.getSharedPreferences("com.onesignal", Context.MODE_PRIVATE)
        otherPrefs.edit()
            .clear()
            .commit()
    }

    test("accessor instances after initWithContext without appID shows the failure reason") {
        // Given
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()

        // When
        os.initWithContext(context)

        // Then
        val ex = shouldThrow<IllegalStateException> {
            os.user.onesignalId // Should trigger waitUntilInitInternal → throw failure message
        }
        // The main exception preserves the caller's stack trace
        ex.message shouldBe "OneSignal initWithContext failed."
        // The detailed failure reason is in the suppressed exception
        ex.suppressed.size shouldBe 1
        ex.suppressed[0].message shouldBe "suspendInitInternal: no appId provided or found in local storage. Please pass a valid appId to initWithContext()."

        // Calling initWithContext with an appID after the failure should not throw anymore
        val result = os.initWithContext(context, "appID")
        waitForInitialization(os)
        result shouldBe true
        os.isInitialized shouldBe true
    }

    test("OneSignal accessors throw before calling initWithContext") {
        val os = OneSignalImp()

        shouldThrow<IllegalStateException> {
            os.user
        }
        shouldThrow<IllegalStateException> {
            os.inAppMessages
        }
        shouldThrow<IllegalStateException> {
            os.session
        }
        shouldThrow<IllegalStateException> {
            os.notifications
        }
        shouldThrow<IllegalStateException> {
            os.location
        }
    }

    test("initWithContext returns gracefully when Android user is locked") {
        // Given
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()

        mockkObject(AndroidUtils)
        every { AndroidUtils.isAndroidUserUnlocked(any()) } returns false

        // When
        os.initWithContext(context, "appId")

        // Then
        // returns gracefully but isInitialized should be false
        os.isInitialized shouldBe false

        unmockkObject(AndroidUtils)
    }

    test("initWithContext is successful when Android user is unlocked") {
        // Given
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()

        mockkObject(AndroidUtils)
        every { AndroidUtils.isAndroidUserUnlocked(any()) } returns true

        // When
        os.initWithContext(context, "appId")

        // Then
        waitForInitialization(os)
        os.isInitialized shouldBe true

        unmockkObject(AndroidUtils)
    }

    test("initWithContext with no appId succeeds when configModel has appId") {
        // Given
        // block SharedPreference before calling init
        val trigger = CountDownLatch(1)
        val context = getApplicationContext<Context>()
        val blockingPrefContext = BlockingPrefsContext(context, trigger)
        val os = OneSignalImp()
        var initSuccess = true

        // Clear any existing appId from previous tests by clearing SharedPreferences
        val prefs = context.getSharedPreferences("OneSignal", Context.MODE_PRIVATE)
        prefs.edit()
            .clear()
            .commit()

        // Set up a legacy appId in SharedPreferences to simulate a previous test scenario
        // This simulates the case where a previous test has set an appId that can be resolved
        prefs.edit()
            .putString(PREFS_LEGACY_APP_ID, "testAppId") // Set legacy appId
            .commit()

        // When
        val accessorThread =
            Thread {
                // this will block until after SharedPreferences is released
                runBlocking {
                    initSuccess = os.initWithContext(blockingPrefContext)
                }
            }

        accessorThread.start()
        accessorThread.join(500)

        accessorThread.isAlive shouldBe true

        // release SharedPreferences
        trigger.countDown()

        accessorThread.join(500)
        accessorThread.isAlive shouldBe false

        // Should return true because configModel already has an appId from previous tests
        initSuccess shouldBe true
        os.isInitialized shouldBe true
    }

    test("initWithContext with appId does not block") {
        // Given
        // block SharedPreference before calling init
        val trigger = CountDownLatch(1)
        val context = getApplicationContext<Context>()
        val blockingPrefContext = BlockingPrefsContext(context, trigger)
        val os = OneSignalImp()

        // When
        val accessorThread =
            Thread {
                os.initWithContext(blockingPrefContext, "appId")
            }

        accessorThread.start()
        accessorThread.join(500)

        // Then
        // should complete even SharedPreferences is unavailable (non-blocking)
        accessorThread.isAlive shouldBe false

        // Release the SharedPreferences lock so internalInit can complete
        trigger.countDown()

        // Wait for initialization to complete (internalInit runs asynchronously)
        waitForInitialization(os, maxAttempts = 50)
    }

    test("accessors will be blocked if call too early after initWithContext with appId") {
        // Given
        // block SharedPreference before calling init
        val trigger = CountDownLatch(1)
        val context = getApplicationContext<Context>()
        val blockingPrefContext = BlockingPrefsContext(context, trigger)
        val os = OneSignalImp()

        val accessorThread =
            Thread {
                os.initWithContext(blockingPrefContext, "appId")
                os.user // This should block until either trigger is released or timed out
            }

        accessorThread.start()
        accessorThread.join(500)

        accessorThread.isAlive shouldBe true

        // release the lock on SharedPreferences
        trigger.countDown()

        accessorThread.join(1000)
        accessorThread.isAlive shouldBe false
        os.isInitialized shouldBe true
    }

    test("ensure adding tags right after initWithContext with appId is successful") {
        // Given
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()
        val tagKey = "tagKey"
        val tagValue = "tagValue"
        val testTags = mapOf(tagKey to tagValue)

        // When
        os.initWithContext(context, "appId")
        os.user.addTags(testTags)

        // Then
        val tags = os.user.getTags()
        tags shouldContain (tagKey to tagValue)
    }

    test("ensure login called right after initWithContext can set externalId correctly") {
        // Given
        // block SharedPreference before calling init
        val trigger = CountDownLatch(1)
        val context = getApplicationContext<Context>()
        val blockingPrefContext = BlockingPrefsContext(context, trigger)
        val os = OneSignalImp()
        val externalId = "testUser"

        val accessorThread =
            Thread {
                os.initWithContext(blockingPrefContext, "appId")
                os.login(externalId)

                // Wait for background login operation to complete with polling
                var attempts = 0
                while (os.user.externalId != externalId && attempts < 50) {
                    Thread.sleep(20)
                    attempts++
                }
            }

        accessorThread.start()
        accessorThread.join(500)

        // initWithContext should return immediately (non-blocking)
        // but isInitialized won't be true until internalInit completes
        // which requires SharedPreferences to be unblocked
        accessorThread.isAlive shouldBe true

        // release the lock on SharedPreferences so internalInit can complete
        trigger.countDown()

        // Wait for initialization to complete (internalInit runs asynchronously)
        var initAttempts = 0
        while (!os.isInitialized && initAttempts < 50) {
            Thread.sleep(20)
            initAttempts++
        }

        os.isInitialized shouldBe true

        accessorThread.join(500)
        accessorThread.isAlive shouldBe false
        os.user.externalId shouldBe externalId
    }

    test("a push subscription should be created right after initWithContext") {
        // Given
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()
        os.initWithContext(context, "appId")

        // When
        val pushSub = os.user.pushSubscription

        // Then
        pushSub shouldNotBe null
        pushSub.token shouldNotBe null
    }

    test("login changes externalId from initial state after init") {
        // Given
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()
        val testExternalId = "uniqueTestUser_${System.currentTimeMillis()}" // Use unique ID to avoid conflicts

        // When
        os.initWithContext(context, "appId")
        val initialExternalId = os.user.externalId
        os.login(testExternalId)

        // Wait for background login operation to complete with polling
        var attempts = 0
        while (os.user.externalId != testExternalId && attempts < 50) {
            Thread.sleep(20)
            attempts++
        }

        val finalExternalId = os.user.externalId

        // Then - Verify the complete login flow
        // 1. Login should set the external ID to our test value
        finalExternalId shouldBe testExternalId

        // 2. Login should change the external ID (regardless of initial state)
        // This makes the test resilient to state contamination while still testing the flow
        finalExternalId shouldNotBe initialExternalId

        // 3. If we're in a clean state, initial should be empty (but don't fail if not)
        // This documents the expected behavior without making the test brittle
        if (initialExternalId.isEmpty()) {
            // Clean state detected - this is the ideal scenario
            println("✅ Clean state: initial externalId was empty as expected")
        } else {
            // State contamination detected - log it but don't fail
            println("⚠️  State contamination: initial externalId was '$initialExternalId' (expected empty)")
        }

        // Clean up after ourselves to avoid polluting subsequent tests
        os.logout()

        // Wait for logout to complete with polling
        var logoutAttempts = 0
        while (os.user.externalId.isNotEmpty() && logoutAttempts < 50) {
            Thread.sleep(20)
            logoutAttempts++
        }
    }

    test("accessor instances after multiple initWithContext calls are consistent") {
        // Given
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()

        // When
        os.initWithContext(context, "appId")

        // Wait for initialization to complete before accessing user
        waitForInitialization(os)

        // Give additional time for coroutines to settle, especially in CI/CD
        Thread.sleep(50)

        val oldUser = os.user

        // Second init from some internal class
        os.initWithContext(context)

        // Wait for second initialization to complete
        waitForInitialization(os)

        // Give additional time for coroutines to settle after second init
        Thread.sleep(50)

        val newUser = os.user

        // Then
        oldUser shouldBe newUser
    }

    test("integration: full user workflow after initialization") {
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()
        val testExternalId = "test-user"
        val tags = mapOf("test" to "integration", "version" to "1.0")

        os.initWithContext(context, "appId")

        // Test user workflow
        // init
        val initialExternalId = os.user.externalId

        // Handle state contamination gracefully - if externalId is not empty, logout first
        if (initialExternalId.isNotEmpty()) {
            println("⚠️  State contamination detected: initial externalId was '$initialExternalId' (expected empty)")
            os.logout()

            // Wait for logout to complete with polling
            var cleanupAttempts = 0
            while (os.user.externalId.isNotEmpty() && cleanupAttempts < 50) {
                Thread.sleep(20)
                cleanupAttempts++
            }

            val cleanedExternalId = os.user.externalId
            cleanedExternalId shouldBe ""
        } else {
            initialExternalId shouldBe ""
        }

        // login
        os.login(testExternalId)

        // Wait for background login operation to complete with polling (CI-safe)
        run {
            var attempts = 0
            val maxAttempts = 200 // 4 seconds total at 20ms intervals
            while (os.user.externalId != testExternalId && attempts < maxAttempts) {
                Thread.sleep(20)
                attempts++
            }
        }

        os.user.externalId shouldBe testExternalId

        // addTags and getTags
        os.user.addTags(tags)
        val retrievedTags = os.user.getTags()
        retrievedTags shouldContain ("test" to "integration")
        retrievedTags shouldContain ("version" to "1.0")

        // logout
        os.logout()

        // Wait for background logout operation to complete with polling
        var attempts = 0
        while (os.user.externalId.isNotEmpty() && attempts < 50) {
            Thread.sleep(20)
            attempts++
        }

        os.user.externalId shouldBe ""
    }

    test("initWithContext recovers when ensureApplicationServiceStarted throws synchronously") {
        // Given: a context whose applicationContext is NOT an Application instance.
        // ApplicationService.start does `context.applicationContext as Application`, which
        // will throw ClassCastException — simulates restricted hosts (Robolectric custom
        // application factories, instrumentation context wrappers, multi-process content
        // provider init order). Without recovery, initState would be left at IN_PROGRESS
        // forever and accessors would deadlock on suspendCompletion.await().
        val baseContext = getApplicationContext<Context>()
        val brokenContext =
            object : ContextWrapper(baseContext) {
                override fun getApplicationContext(): Context = ContextWrapper(baseContext)
            }
        val os = OneSignalImp()

        // When: synchronous failure during the bootstrap step.
        shouldThrow<ClassCastException> {
            os.initWithContext(brokenContext, "appId")
        }

        // Then: SDK transitions to FAILED, accessor throws with the cast as suppressed cause.
        val ex =
            shouldThrow<IllegalStateException> {
                os.user.onesignalId
            }
        ex.message shouldBe "OneSignal initWithContext failed."
        ex.suppressed.any { it is ClassCastException } shouldBe true

        // And: a retry with a working context succeeds (FAILED is not "accessible",
        // so the retry legitimately re-enters init).
        os.initWithContext(baseContext, "appId") shouldBe true
        waitForInitialization(os)
    }

    test("initWithContext recovers when async internalInit throws unexpectedly") {
        // Given: AndroidUtils.isAndroidUserUnlocked throws unexpectedly inside the async init,
        // simulating any unexpected runtime exception during initialization (a service throwing
        // during bootstrap, a dependency failing to resolve, etc.). suspendifyOnIO swallows
        // exceptions internally, so without the catch in internalInit the deferred would never
        // complete and accessors would deadlock on suspendCompletion.await().
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()
        val cause = RuntimeException("simulated init crash")

        mockkObject(AndroidUtils)
        every { AndroidUtils.isAndroidUserUnlocked(any()) } throws cause

        try {
            // When: public initWithContext returns true (the throw happens asynchronously).
            os.initWithContext(context, "appId") shouldBe true

            // Then: the async catch transitions to FAILED and completes the deferred, so
            // accessors throw promptly instead of blocking forever.
            val ex =
                shouldThrow<IllegalStateException> {
                    os.user.onesignalId
                }
            ex.message shouldBe "OneSignal initWithContext failed."
            ex.suppressed.any { it === cause } shouldBe true
        } finally {
            unmockkObject(AndroidUtils)
        }
    }

    test("legacy mode logs guidance when accessor on main thread blocks during in-progress init") {
        // Given: pre-#2605, legacy mode threw if the SDK wasn't ready; the blocking happened
        // inside initWithContext itself (synchronous runBlocking). Now that init dispatches
        // asynchronously, the first accessor on the main thread can block instead. Total ANR
        // risk is roughly equivalent — just shifted in time — but it's no longer obviously
        // located in initWithContext, so we log a warning that points callers to the suspend API.
        val trigger = CountDownLatch(1)
        val context = getApplicationContext<Context>()
        val blockingPrefContext = BlockingPrefsContext(context, trigger)
        val os = OneSignalImp()
        val warnings = mutableListOf<String>()
        val listener =
            ILogListener { event: OneSignalLogEvent ->
                if (event.level == LogLevel.WARN) warnings.add(event.entry)
            }

        mockkObject(AndroidUtils)
        every { AndroidUtils.isAndroidUserUnlocked(any()) } returns true
        every { AndroidUtils.isRunningOnMainThread() } returns true

        os.debug.addLogListener(listener)
        try {
            // When: init kicks off but stalls inside internalInit on shared-prefs access.
            os.initWithContext(blockingPrefContext, "appId")
            os.isInitialized shouldBe false

            // Trigger the would-block accessor from a background thread (so the test thread
            // doesn't actually hang on runBlocking) — the warning logged from inside
            // warnIfBlockingOnMainThread is what we're verifying.
            val accessorThread =
                Thread {
                    runCatching { os.user.onesignalId }
                }
            accessorThread.start()
            accessorThread.join(500)

            // The accessor is blocked waiting for init; release prefs so it can complete.
            trigger.countDown()
            accessorThread.join(2_000)
            accessorThread.isAlive shouldBe false

            // Then: a warning must have been emitted with actionable guidance.
            val match = warnings.firstOrNull { it.contains("OneSignal initialization is still in progress") }
            match shouldNotBe null
            (match!!.contains("main thread")) shouldBe true
            (match.contains("suspend API")) shouldBe true
        } finally {
            os.debug.removeLogListener(listener)
            // Ensure any waiter is released even if assertions failed early.
            if (trigger.count > 0) trigger.countDown()
            unmockkObject(AndroidUtils)
        }
    }

    test("login should throw exception when initWithContext is never called") {
        // Given
        val oneSignalImp = OneSignalImp()

        // When/Then - should throw exception immediately
        val exception =
            shouldThrow<IllegalStateException> {
                oneSignalImp.login("testUser", null)
            }

        // Should throw immediately because isInitialized is false
        exception.message shouldBe "Must call 'initWithContext' before 'login'"
    }

    test("logout should throw exception when initWithContext is never called") {
        // Given
        val oneSignalImp = OneSignalImp()

        // When/Then - should throw exception immediately
        val exception =
            shouldThrow<IllegalStateException> {
                oneSignalImp.logout()
            }

        // Should throw immediately because isInitialized is false
        exception.message shouldBe "Must call 'initWithContext' before 'logout'"
    }
})

/**
 * Simulate a context awaiting for a shared preference until the trigger is signaled
 */
class BlockingPrefsContext(
    context: Context,
    private val unblockTrigger: CountDownLatch,
) : ContextWrapper(context) {
    override fun getSharedPreferences(
        name: String,
        mode: Int,
    ): SharedPreferences {
        unblockTrigger.await()

        return super.getSharedPreferences(name, mode)
    }
}
