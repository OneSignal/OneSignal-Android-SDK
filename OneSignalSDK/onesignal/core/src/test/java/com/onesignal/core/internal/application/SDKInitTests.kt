package com.onesignal.core.internal.application

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.AndroidUtils
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID
import com.onesignal.debug.LogLevel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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

    fun withSaturatedOneSignalIo(block: () -> Unit) {
        val running = CountDownLatch(2)
        val release = CountDownLatch(1)
        repeat(4) {
            OneSignalDispatchers.launchOnIO {
                running.countDown()
                release.await(30, TimeUnit.SECONDS)
            }
        }

        try {
            running.await(5, TimeUnit.SECONDS) shouldBe true
            block()
        } finally {
            release.countDown()
        }
    }

    fun runOnThreadAndWait(block: () -> Unit) {
        val error = AtomicReference<Throwable?>(null)
        val thread =
            Thread {
                try {
                    block()
                } catch (t: Throwable) {
                    error.set(t)
                }
            }
        thread.start()
        thread.join(2_000)

        thread.isAlive shouldBe false
        error.get() shouldBe null
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
        // Init runs asynchronously and fails the locked-storage check, transitioning to FAILED.
        // Reading an accessor blocks until that terminal state and surfaces the failure.
        shouldThrow<IllegalStateException> {
            os.user.onesignalId
        }
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
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()

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

        // When - no appId passed; resolved from the legacy SharedPreferences value.
        os.initWithContext(context)

        // Then - init completes successfully because an appId was resolvable.
        waitForInitialization(os)
        os.isInitialized shouldBe true
    }

    test("initWithContext returns immediately and completes init asynchronously") {
        // Background-threaded init is the default: initWithContext dispatches internalInit to a
        // background dispatcher and returns immediately, even while internalInit is parked on a
        // slow SharedPreferences read. isInitialized only flips to true once init completes.
        val trigger = CountDownLatch(1)
        val context = getApplicationContext<Context>()
        val blockingPrefContext = BlockingPrefsContext(context, trigger)
        val os = OneSignalImp()

        // Returns right away despite the blocked prefs read happening on the background worker.
        os.initWithContext(blockingPrefContext, "appId") shouldBe true
        os.isInitialized shouldBe false

        trigger.countDown()
        waitForInitialization(os)
        os.isInitialized shouldBe true
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

    test("accessor returns once init is SUCCESS even when the IO dispatcher is saturated (OneSignal-Flutter-SDK#1163)") {
        // Regression test for the Flutter main-thread ANR. getServiceWithFeatureGate used to
        // ALWAYS route through waitAndReturn -> waitForInit -> runBlocking(OneSignalDispatchers.IO),
        // even after init had already reached SUCCESS. On Flutter's main-thread lifecycleInit that
        // parks the UI thread on a cold-start-contended IO pool (OneSignal-Flutter-SDK#1163). The
        // fix adds a lock-free SUCCESS fast-path, centralized in waitForInit().
        //
        // We reproduce the contention faithfully: saturate OneSignalDispatchers.IO so that the
        // pre-fix runBlocking(OneSignalDispatchers.IO) would park indefinitely behind the busy
        // workers, then assert the accessor still returns. With the fix it answers synchronously;
        // without it the accessor thread would still be parked when we check.
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()

        os.initWithContext(context, "appId")
        waitForInitialization(os)

        withSaturatedOneSignalIo {
            // When: a SUCCESS-state accessor is read while the IO pool is fully saturated.
            val result = arrayOfNulls<Any>(1)
            runOnThreadAndWait { result[0] = os.user }

            // Then: the fast-path answered synchronously and the thread finished. The pre-fix
            // runBlocking(OneSignalDispatchers.IO) path would still be parked here.
            result[0] shouldNotBe null
        }
    }

    test("login returns once init is SUCCESS even when the IO dispatcher is saturated (OneSignal-Flutter-SDK#1163)") {
        // Companion regression test proving the SUCCESS fast-path is centralized in waitForInit(),
        // so it covers the operation paths (login / logout / updateUserJwt / JWT listeners), not
        // just the service accessors. login() used to route through
        // waitForInit -> runBlocking(OneSignalDispatchers.IO) even after init reached SUCCESS, so a
        // saturated IO pool would park the caller indefinitely.
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()

        os.initWithContext(context, "appId")
        waitForInitialization(os)

        withSaturatedOneSignalIo {
            // When: login() is invoked while the IO pool is fully saturated. waitForInit must
            // short-circuit (the subsequent enqueue is fire-and-forget via suspendifyOnIO and does
            // not block the caller).
            runOnThreadAndWait { os.login("externalId") }

            // Then: the fast-path answered synchronously and the call returned. The pre-fix
            // runBlocking(OneSignalDispatchers.IO) path would still be parked here.
        }
    }

    test("consent getters return once init is SUCCESS even when the IO dispatcher is saturated") {
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()

        os.initWithContext(context, "appId")
        waitForInitialization(os)

        withSaturatedOneSignalIo {
            val results = arrayOfNulls<Boolean>(3)
            runOnThreadAndWait {
                results[0] = os.consentRequired
                results[1] = os.consentGiven
                results[2] = os.disableGMSMissingPrompt
            }

            results.toList() shouldBe listOf(false, false, false)
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

    // Regression test for the SessionService NPE during in-flight background init.
    //
    // Background: when init is in flight, internalInit() has not yet called bootstrapServices(),
    // which means IBootstrapService implementations like SessionService have null model fields.
    // SyncJobService re-enters via initWithContextSuspend(context, null) and then immediately
    // calls runBackgroundServices(); if initWithContextSuspend returns true while init is still
    // in progress (the old behavior), the loop hits SessionService.endSession() → NPE.
    //
    // The fix: initWithContextSuspend must suspend until init is *fully* completed, not just
    // kicked off. This test verifies that contract by checking that os.isInitialized is true
    // at the moment the re-entrant suspend call returns.
    test("initWithContextSuspend with in-flight init waits for completion before returning") {
        val context = getApplicationContext<Context>()
        val trigger = CountDownLatch(1)
        // started signals when the first caller has entered internalInit() and reached the
        // blocking SharedPreferences access -- by which point initState is IN_PROGRESS.
        val started = CountDownLatch(1)
        val blockingCtx = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                started.countDown()
                // Bounded await -- if the test logic ever fails to release `trigger`, the
                // IO worker exits cleanly instead of blocking forever and deadlocking the suite.
                trigger.await(30, TimeUnit.SECONDS)
                return super.getSharedPreferences(name, mode)
            }
        }
        val os = OneSignalImp()

        runBlocking {
            // First caller: stalls inside internalInit() once it tries to read prefs.
            val firstInit = async(Dispatchers.IO) {
                os.initWithContextSuspend(blockingCtx, "appId")
            }

            // Deterministically wait until firstInit has entered the prefs-blocking region.
            // After this point, initState is guaranteed to be IN_PROGRESS.
            started.await(5, TimeUnit.SECONDS) shouldBe true
            os.isInitialized shouldBe false
            firstInit.isCompleted shouldBe false

            // Second caller: under the OLD behavior the synchronized(initLock) block sees
            // state == IN_PROGRESS and returns `true` *while* internalInit() (running on the
            // first caller) has not yet reached `initState = InitState.SUCCESS`. Under the
            // fix, this call must suspend until the first caller fully completes init -- so
            // that os.isInitialized is guaranteed `true` at the moment we return.
            //
            // We capture isInitialized at exactly the moment initWithContextSuspend returns
            // for the second caller. Old code: false (bug). New code: true (fix).
            val secondInit = async(Dispatchers.IO) {
                val result = os.initWithContextSuspend(context, "appId")
                val initializedAtReturn = os.isInitialized
                Pair(result, initializedAtReturn)
            }

            // Sanity: the second caller has not pre-empted the test by returning before
            // we unblock the first caller (timing depends on lazy ServiceProvider locks).
            Thread.sleep(200)

            // Unblock the first caller so internalInit() can complete (state -> SUCCESS).
            trigger.countDown()

            firstInit.await() shouldBe true
            val (secondResult, initializedAtReturn) = secondInit.await()
            secondResult shouldBe true
            // KEY assertion: os must be fully initialized at the moment the re-entrant
            // suspend init returns. Old code violated this by returning early while state
            // was still IN_PROGRESS, which is what allowed SyncJobService to reach
            // runBackgroundServices() before bootstrap() had run.
            initializedAtReturn shouldBe true
            os.isInitialized shouldBe true
        }
    }

    // Regression test for review-flagged Defect 1 (stale latch on retry-after-FAILED).
    //
    // Background: `suspendCompletion` was a single-shot `val CompletableDeferred<Unit>`. Once
    // any init terminates (even FAILED), the deferred stays permanently complete -- so a
    // re-entrant suspend caller arriving DURING a subsequent retry would `await()` on the
    // already-completed deferred, return instantly, and read transient state (likely
    // IN_PROGRESS -> false), silently dropping JobService work.
    //
    // The fix resets `suspendCompletion` whenever the synchronized(initLock) block flips state
    // into IN_PROGRESS, and await sites local-capture the deferred under the lock.
    test("initWithContextSuspend resets latch on retry-after-FAILED") {
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()

        runBlocking {
            // 1. Force a deterministic FAILED first init via the user-locked branch
            //    (matches the pattern used by other tests in this file).
            mockkObject(AndroidUtils)
            every { AndroidUtils.isAndroidUserUnlocked(any()) } returns false
            val firstInit = os.initWithContextSuspend(context, "appId")
            firstInit shouldBe false
            os.isInitialized shouldBe false
            // At this point the (pre-fix) single-shot `suspendCompletion` is permanently complete.
            unmockkObject(AndroidUtils)

            // 2. Stall a fresh retry via BlockingPrefsContext so initState sits at IN_PROGRESS.
            val started = CountDownLatch(1)
            val trigger = CountDownLatch(1)
            val blockingCtx = object : ContextWrapper(context) {
                override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                    started.countDown()
                    // Bounded await -- if the test logic ever fails to release `trigger`, the
                    // IO worker exits cleanly instead of blocking forever and deadlocking the suite.
                    trigger.await(30, TimeUnit.SECONDS)
                    return super.getSharedPreferences(name, mode)
                }
            }

            val secondInit = async(Dispatchers.IO) { os.initWithContextSuspend(blockingCtx, "appId") }
            started.await(5, TimeUnit.SECONDS) shouldBe true
            os.isInitialized shouldBe false
            secondInit.isCompleted shouldBe false

            // 3. Re-entrant caller. With Defect 1, it would wake immediately on the
            //    FAILED-generation latch (still permanently complete) and return based on
            //    transient state. With the fix, it waits on the *new* generation's latch.
            val thirdInit = async(Dispatchers.IO) {
                val r = os.initWithContextSuspend(context, "appId")
                val initializedAtReturn = os.isInitialized
                Pair(r, initializedAtReturn)
            }

            Thread.sleep(300)
            thirdInit.isCompleted shouldBe false // would be true with the stale-latch bug

            // 4. Release the second init; both new callers complete with state == SUCCESS.
            trigger.countDown()

            secondInit.await() shouldBe true
            val (thirdResult, thirdInitializedAtReturn) = thirdInit.await()
            thirdResult shouldBe true
            thirdInitializedAtReturn shouldBe true
            os.isInitialized shouldBe true
        }
    }

    // Regression test for review-flagged Defect 2 (indefinite hang if internalInit throws).
    //
    // Background: `internalInit` had no try/catch wrapping its body. An unchecked throw from
    // initEssentials/bootstrapServices/etc. would leave initState=IN_PROGRESS forever and
    // `suspendCompletion` uncompleted -- causing every re-entrant suspend caller (e.g.
    // SyncJobService) to hang on `await()` indefinitely, holding its budget slot until the
    // OS killed the worker.
    //
    // The fix wraps internalInit's body in try/catch, ensuring a terminal state and
    // `notifyInitComplete()` on any throw.
    test("initWithContextSuspend reaches terminal state when internalInit throws") {
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()

        // Make the very first call inside internalInit throw (mirrors the user-locked test
        // pattern, but with a throw instead of a `false` return). This is the cheapest way to
        // simulate "any unchecked exception during bootstrap" without coupling to specific
        // bootstrap internals.
        mockkObject(AndroidUtils)
        every { AndroidUtils.isAndroidUserUnlocked(any()) } throws RuntimeException("simulated bootstrap failure")

        runBlocking {
            // Without the fix: the throw propagates out of internalInit, escapes withContext,
            // and either fails the test outright or leaves the SDK in a deadlocked
            // IN_PROGRESS state with `suspendCompletion` never completed.
            // With the fix: the catch block sets FAILED + notifyInitComplete + returns false.
            val firstResult = withTimeoutOrNull(5_000) {
                os.initWithContextSuspend(context, "appId")
            }
            firstResult shouldBe false
            os.isInitialized shouldBe false

            unmockkObject(AndroidUtils)

            // State is FAILED so a retry is allowed and (per Defect 1 fix) gets a fresh latch.
            // This also doubles as a smoke test that we didn't leak IN_PROGRESS.
            val retry = withTimeoutOrNull(5_000) {
                os.initWithContextSuspend(context, "appId")
            }
            retry shouldBe true
            os.isInitialized shouldBe true
        }
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
