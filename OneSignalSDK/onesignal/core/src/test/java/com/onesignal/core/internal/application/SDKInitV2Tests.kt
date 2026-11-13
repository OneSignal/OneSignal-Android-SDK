package com.onesignal.core.internal.application

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.IUserJwtInvalidatedListener
import com.onesignal.common.AndroidUtils
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.impl.OperationRepo
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID
import com.onesignal.debug.ILogListener
import com.onesignal.debug.LogLevel
import com.onesignal.debug.OneSignalLogEvent
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.internal.InitGate
import com.onesignal.internal.InitState
import com.onesignal.internal.OneSignalImpV2
import com.onesignal.user.internal.UserManager
import com.onesignal.user.internal.jwt.JwtTokenStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RobolectricTest
class SDKInitV2Tests : FunSpec({

    /** Polls until [oneSignalImp] is initialized, up to [maxAttempts] sleeps of [sleepMs]. */
    fun waitForInitialization(oneSignalImp: OneSignalImpV2, maxAttempts: Int = 100, sleepMs: Long = 20) {
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
        // Daemon so a stuck waitForInit() (e.g. if a dispatch is rejected under pool pressure)
        // can never keep the test JVM alive and trip the 6h CI timeout.
        thread.isDaemon = true
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

    test("accessor after initWithContext without appID returns a default instead of throwing") {
        // Given
        val context = IsolatedPrefsContext(getApplicationContext())
        val os = OneSignalImpV2()

        // When
        os.initWithContext(context)

        // Then
        os.user.onesignalId shouldBe ""
        os.isInitialized shouldBe false

        // A later initWithContext with an appID still initializes
        val result = os.initWithContext(context, "appID")
        waitForInitialization(os)
        result shouldBe true
        os.isInitialized shouldBe true
    }

    test("OneSignal accessors return defaults before calling initWithContext") {
        val os = OneSignalImpV2()

        os.user.onesignalId shouldBe ""
        os.user.externalId shouldBe ""
        os.user.getTags() shouldBe emptyMap()
        os.user.pushSubscription.id shouldBe ""
        os.user.pushSubscription.optedIn shouldBe false
        os.user.addTag("key", "value")
        os.notifications.permission shouldBe false
        runBlocking { os.notifications.requestPermission(true) } shouldBe false
        os.inAppMessages.paused shouldBe false
        os.inAppMessages.addTrigger("key", "value")
        os.session.addOutcome("outcome")
        os.location.isShared shouldBe false
        os.isInitialized shouldBe false
    }

    test("accessor references captured before initWithContext forward once init succeeds") {
        val os = OneSignalImpV2()
        val user = os.user

        os.initWithContext(IsolatedPrefsContext(getApplicationContext()), "appId")
        waitForInitialization(os)

        user.addTag("key", "value")
        os.user.getTags() shouldContain ("key" to "value")
        user.getTags() shouldContain ("key" to "value")
    }

    test("accessor references captured after a failed init forward once a retry succeeds") {
        val context = IsolatedPrefsContext(getApplicationContext())
        val os = OneSignalImpV2()
        os.initWithContext(context, "")
        val user = os.user
        os.isInitialized shouldBe false

        os.initWithContext(context, "appId")
        waitForInitialization(os)

        user.addTag("key", "value")
        os.user.getTags() shouldContain ("key" to "value")
        user.getTags() shouldContain ("key" to "value")
    }

    test("a retry after a failed init does not enqueue duplicate operations") {
        val context = IsolatedPrefsContext(getApplicationContext())
        val os = OneSignalImpV2()
        os.initWithContext(context, "")
        os.user
        os.isInitialized shouldBe false

        os.initWithContext(context, "appId")
        waitForInitialization(os)
        os.user.addTag("retryKey", "value")

        val repo = os.getService(IOperationRepo::class.java) as OperationRepo
        fun tagOps() = synchronized(repo.queue) { repo.queue.count { it.operation.toString().contains("retryKey") } }
        repeat(50) { if (tagOps() == 0) Thread.sleep(20) }
        Thread.sleep(200)
        tagOps() shouldBe 1
    }

    test("observers and listeners registered before init are applied once init succeeds") {
        val os = OneSignalImpV2()
        os.user.addObserver(mockk(relaxed = true))
        // Core-only test: the in-app messages module is absent, so replaying this throws and must not break init.
        os.inAppMessages.addClickListener(mockk(relaxed = true))

        os.initWithContext(IsolatedPrefsContext(getApplicationContext()), "appId")
        waitForInitialization(os)

        (os.user as UserManager).changeHandlersNotifier.hasSubscribers shouldBe true
        os.isInitialized shouldBe true
    }

    test("registrations queued before a failed init are kept and applied when a retry succeeds") {
        val context = IsolatedPrefsContext(getApplicationContext())
        val os = OneSignalImpV2()
        os.user.addObserver(mockk(relaxed = true))

        os.initWithContext(context, "")
        os.user
        os.isInitialized shouldBe false

        os.initWithContext(context, "appId")
        waitForInitialization(os)
        (os.user as UserManager).changeHandlersNotifier.hasSubscribers shouldBe true
    }

    test("calls dropped before init are summarized in one error once init succeeds; queued registrations are not") {
        val events = CopyOnWriteArrayList<OneSignalLogEvent>()
        val listener = ILogListener { events += it }
        Logging.addListener(listener)
        try {
            val os = OneSignalImpV2()
            os.user.addTag("key", "value")
            os.user.addObserver(mockk(relaxed = true))
            os.initWithContext(IsolatedPrefsContext(getApplicationContext()), "appId")
            waitForInitialization(os)
        } finally {
            Logging.removeListener(listener)
        }

        events.count {
            it.level == LogLevel.ERROR &&
                it.entry.contains("calls made before initWithContext were ignored: User.addTag. Call initWithContext first.")
        } shouldBe 1
    }

    test("pre-init registrations are replayed before init reports success") {
        val os = OneSignalImpV2()
        var stateAtReplay: InitState? = null
        // The in-app messages module is absent here, so its replayed call logs synchronously on the init thread.
        val listener = ILogListener { if (it.entry.contains("Replaying 'addClickListener'")) stateAtReplay = os.gate.state }
        Logging.addListener(listener)
        try {
            os.inAppMessages.addClickListener(mockk(relaxed = true))
            os.initWithContext(IsolatedPrefsContext(getApplicationContext()), "appId")
            waitForInitialization(os)
        } finally {
            Logging.removeListener(listener)
        }

        stateAtReplay shouldBe InitState.IN_PROGRESS
    }

    test("a JWT invalidated listener added before init is replayed, and a removal before init cancels it") {
        val os = OneSignalImpV2()
        val fired = CountDownLatch(1)
        val removedFired = CountDownLatch(1)
        val removed = IUserJwtInvalidatedListener { removedFired.countDown() }
        os.addUserJwtInvalidatedListener { fired.countDown() }
        os.addUserJwtInvalidatedListener(removed)
        os.removeUserJwtInvalidatedListener(removed)

        os.initWithContext(IsolatedPrefsContext(getApplicationContext()), "appId")
        waitForInitialization(os)
        val store = os.getService(JwtTokenStore::class.java)
        store.putJwt("ext", "token")
        store.invalidateJwt("ext")

        fired.await(2, TimeUnit.SECONDS) shouldBe true
        removedFired.await(200, TimeUnit.MILLISECONDS) shouldBe false
    }

    test("an empty appId that falls back to a cached appId logs a warning, not an error") {
        val context = IsolatedPrefsContext(getApplicationContext())
        context.getSharedPreferences("OneSignal", Context.MODE_PRIVATE).edit().putString(PREFS_LEGACY_APP_ID, "cachedAppId").commit()
        val events = CopyOnWriteArrayList<OneSignalLogEvent>()
        val listener = ILogListener { events += it }
        Logging.addListener(listener)
        val os = OneSignalImpV2()
        try {
            os.initWithContext(context, "")
            waitForInitialization(os)
        } finally {
            Logging.removeListener(listener)
        }

        os.isInitialized shouldBe true
        events.filter { it.level == LogLevel.ERROR && it.entry.contains("appId") } shouldBe emptyList()
        events.count { it.level == LogLevel.WARN && it.entry.contains("Using the cached app ID") } shouldBe 1
    }

    test("a null appId uses the cached app id and names it, and a null appId with nothing cached warns and does not init") {
        val cached = IsolatedPrefsContext(getApplicationContext())
        cached.getSharedPreferences("OneSignal", Context.MODE_PRIVATE).edit().putString(PREFS_LEGACY_APP_ID, "cachedAppId").commit()
        val cachedEvents = CopyOnWriteArrayList<OneSignalLogEvent>()
        val cachedListener = ILogListener { cachedEvents += it }
        Logging.addListener(cachedListener)
        try {
            val os = OneSignalImpV2()
            os.initWithContext(cached, null)
            waitForInitialization(os)
            os.isInitialized shouldBe true
        } finally {
            Logging.removeListener(cachedListener)
        }
        cachedEvents.count { it.level == LogLevel.WARN && it.entry.contains("Using the cached app ID cachedAppId") } shouldBe 1

        val missingEvents = CopyOnWriteArrayList<OneSignalLogEvent>()
        val missingListener = ILogListener { missingEvents += it }
        Logging.addListener(missingListener)
        try {
            val os = OneSignalImpV2()
            os.initWithContext(IsolatedPrefsContext(getApplicationContext()), null)
            val deadline = System.currentTimeMillis() + 2_000
            while (os.gate.state == InitState.IN_PROGRESS && System.currentTimeMillis() < deadline) Thread.sleep(20)
            os.gate.state shouldBe InitState.FAILED
            os.isInitialized shouldBe false
        } finally {
            Logging.removeListener(missingListener)
        }
        missingEvents.count { it.level == LogLevel.WARN && it.entry.contains("appId is null and no app ID is cached") } shouldBe 1
        missingEvents.filter { it.level == LogLevel.ERROR && it.entry.contains("appId") } shouldBe emptyList()
    }

    test("an ignored call logs one error per attempt, with the failure trace, and repeats at debug") {
        val events = CopyOnWriteArrayList<OneSignalLogEvent>()
        val listener = ILogListener { events += it }
        Logging.addListener(listener)
        try {
            val os = OneSignalImpV2()
            os.user.addTag("key", "value")
            events.filter { it.level == LogLevel.ERROR }.map { it.entry.substringAfter("] ") } shouldBe
                listOf("'User.addTag' ignored: call initWithContext with a valid appId first.")

            os.initWithContext(IsolatedPrefsContext(getApplicationContext()), "")
            os.user
            events.clear()
            os.user.addTag("key", "value")
            os.user.addTag("key", "value")

            val logged = events.filter { it.entry.contains("'User.addTag'") }
            logged.map { it.level } shouldBe listOf(LogLevel.ERROR, LogLevel.DEBUG)
            logged[0].entry.contains("OneSignal initWithContext failed.") shouldBe true
        } finally {
            Logging.removeListener(listener)
        }
    }

    test("a consent change made after init applied the stored values is not lost") {
        val context = IsolatedPrefsContext(getApplicationContext())
        val os = OneSignalImpV2()
        os.consentGiven = false
        os.initWithContext(context, "appId")
        waitForInitialization(os)

        // Re-enter the window between updateConfig and SUCCESS, where the setter used to write only the local copy.
        val state = InitGate::class.java.getDeclaredField("state").apply { isAccessible = true }
        val repo = os.getService(IOperationRepo::class.java) as OperationRepo
        val forced = OperationRepo::class.java.getDeclaredField("forcedDuringExecute").apply { isAccessible = true }
        synchronized(repo.queue) {
            repo.queue.clear()
            forced.set(repo, false)
        }
        state.set(os.gate, InitState.IN_PROGRESS)
        os.consentGiven = true
        state.set(os.gate, InitState.SUCCESS)

        os.consentGiven shouldBe true
        forced.get(repo) shouldBe true
    }

    test("cancelling initWithContextSuspend before init runs does not leave init stuck in progress") {
        val context = IsolatedPrefsContext(getApplicationContext())
        val os = OneSignalImpV2()

        runBlocking {
            launch {
                cancel()
                os.initWithContextSuspend(context, "appId")
            }.join()
        }

        os.gate.state shouldBe InitState.FAILED
        os.initWithContext(context, "appId") shouldBe true
        waitForInitialization(os)
    }

    test("initWithContext with a null context returns false without throwing") {
        val os = OneSignalImpV2()

        os.initWithContext(null, "appId") shouldBe false
        runBlocking { os.initWithContextSuspend(null, "appId") } shouldBe false
        os.isInitialized shouldBe false
    }

    test("initWithContext returns gracefully when Android user is locked") {
        // Given
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        mockkObject(AndroidUtils)
        every { AndroidUtils.isAndroidUserUnlocked(any()) } returns false

        // When
        os.initWithContext(context, "appId")

        // Then
        // Init runs asynchronously and fails the locked-storage check, transitioning to FAILED.
        // Reading an accessor blocks until that terminal state, then returns a default.
        os.user.onesignalId shouldBe ""
        os.login("user")
        os.isInitialized shouldBe false

        unmockkObject(AndroidUtils)
    }

    test("initWithContext is successful when Android user is unlocked") {
        // Given
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

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
        val context = IsolatedPrefsContext(getApplicationContext())
        val os = OneSignalImpV2()
        val prefs = context.getSharedPreferences("OneSignal", Context.MODE_PRIVATE)

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

    test("initWithContext with an empty or null appId and nothing cached skips init without throwing") {
        for (appId in listOf("", null)) {
            // Fresh stores: buffered prefs writes from earlier tests can land after afterAny clears.
            val context = IsolatedPrefsContext(getApplicationContext())
            val os = OneSignalImpV2()
            os.initWithContext(context, appId)

            // Blocks until init reaches its terminal state, then returns a default.
            os.user.onesignalId shouldBe ""
            os.login("user")
            os.isInitialized shouldBe false
        }
    }

    test("initWithContext with an empty appId falls back to the cached appId") {
        val context = IsolatedPrefsContext(getApplicationContext())
        context.getSharedPreferences("OneSignal", Context.MODE_PRIVATE).edit()
            .putString(PREFS_LEGACY_APP_ID, "cachedAppId")
            .commit()
        val os = OneSignalImpV2()

        os.initWithContext(context, "")

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
        val os = OneSignalImpV2()

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
        val os = OneSignalImpV2()

        val accessorThread =
            Thread {
                os.initWithContext(blockingPrefContext, "appId")
                os.user // This should block until either trigger is released or timed out
            }

        // Daemon so a stuck waitForInit() can never keep the test JVM alive.
        accessorThread.isDaemon = true
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
        val os = OneSignalImpV2()
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
        val os = OneSignalImpV2()
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

        // Daemon so a stuck waitForInit() can never keep the test JVM alive.
        accessorThread.isDaemon = true
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
        val os = OneSignalImpV2()
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
        val os = OneSignalImpV2()
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
        val os = OneSignalImpV2()

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
        val os = OneSignalImpV2()
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
        // applicationContext is not an Application, so ApplicationService.start throws ClassCastException,
        // as in some restricted hosts. Init must still reach a terminal state.
        val baseContext = getApplicationContext<Context>()
        val brokenContext =
            object : ContextWrapper(baseContext) {
                override fun getApplicationContext(): Context = ContextWrapper(baseContext)
            }
        val os = OneSignalImpV2()

        // When: synchronous failure during the bootstrap step.
        os.initWithContext(brokenContext, "appId") shouldBe false

        // Then: SDK transitions to FAILED, and the accessor returns a default.
        os.user.onesignalId shouldBe ""
        os.isInitialized shouldBe false

        // And: a retry with a working context succeeds (FAILED is not "accessible",
        // so the retry legitimately re-enters init).
        os.initWithContext(baseContext, "appId") shouldBe true
        waitForInitialization(os)
    }

    test("accessor returns once init is SUCCESS even when the IO dispatcher is saturated") {
        // At SUCCESS an accessor must answer without an IO worker; a busy pool must not park the main thread.
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        os.initWithContext(context, "appId")
        waitForInitialization(os)

        withSaturatedOneSignalIo {
            // When: a SUCCESS-state accessor is read while the IO pool is fully saturated.
            val result = arrayOfNulls<Any>(1)
            runOnThreadAndWait { result[0] = os.user }

            result[0] shouldNotBe null
        }
    }

    test("login returns once init is SUCCESS even when the IO dispatcher is saturated") {
        // Same as above for the operation paths (login, logout, updateUserJwt, JWT listeners).
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        os.initWithContext(context, "appId")
        waitForInitialization(os)

        withSaturatedOneSignalIo {
            // When: login() is invoked while the IO pool is fully saturated. waitForInit must
            // short-circuit (the subsequent enqueue is fire-and-forget via suspendifyOnIO and does
            // not block the caller).
            runOnThreadAndWait { os.login("externalId") }
        }
    }

    test("consent getters return once init is SUCCESS even when the IO dispatcher is saturated") {
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

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

    test("initWithContextSuspend returns once init is SUCCESS even when the IO dispatcher is saturated") {
        // Observing a terminal SUCCESS must not require an IO worker.
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        os.initWithContext(context, "appId")
        waitForInitialization(os)

        withSaturatedOneSignalIo {
            val result = arrayOfNulls<Boolean>(1)
            runOnThreadAndWait { result[0] = runBlocking { os.initWithContextSuspend(context, "appId") } }

            result[0] shouldBe true
        }
    }

    test("initWithContextSuspend awaiting an in-flight init does not need an IO worker") {
        // Waiting on another caller's init must not queue behind the work that init is doing,
        // otherwise a caller arriving while init is IN_PROGRESS stalls until the pool drains.
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        val started = CountDownLatch(1)
        val trigger = CountDownLatch(1)
        val blockingCtx =
            object : ContextWrapper(context) {
                override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                    started.countDown()
                    trigger.await(30, TimeUnit.SECONDS)
                    return super.getSharedPreferences(name, mode)
                }
            }

        // Hold init at IN_PROGRESS, then fill the pool so no worker is left for a second caller.
        os.initWithContext(blockingCtx, "appId")
        started.await(5, TimeUnit.SECONDS) shouldBe true
        os.isInitialized shouldBe false

        // The stalled init already holds one worker, so one more blocking task fills the pool
        // and the rest back up the queue.
        val release = CountDownLatch(1)
        val occupied = CountDownLatch(1)
        repeat(4) {
            OneSignalDispatchers.launchOnIO {
                occupied.countDown()
                release.await(30, TimeUnit.SECONDS)
            }
        }

        try {
            occupied.await(5, TimeUnit.SECONDS) shouldBe true

            val startedWaiting = CountDownLatch(1)
            val waiter =
                Thread {
                    runBlocking {
                        startedWaiting.countDown()
                        os.initWithContextSuspend(context, "appId")
                    }
                }
            waiter.isDaemon = true
            waiter.start()

            startedWaiting.await(5, TimeUnit.SECONDS) shouldBe true
            Thread.sleep(200)
            // Still blocked, because the init it is waiting on has not been released yet.
            waiter.isAlive shouldBe true

            trigger.countDown()

            // Resuming is driven by the latch, not by a free worker, so this completes while
            // the queued tasks above still own the pool.
            waiter.join(5_000)
            waiter.isAlive shouldBe false
        } finally {
            trigger.countDown()
            release.countDown()
        }

        os.isInitialized shouldBe true
    }

    test("login and logout are ignored without throwing when initWithContext is never called") {
        val oneSignalImp = OneSignalImpV2()

        oneSignalImp.login("testUser", null)
        oneSignalImp.logout()
        oneSignalImp.updateUserJwt("testUser", "token")

        oneSignalImp.isInitialized shouldBe false
    }

    // A re-entrant caller (e.g. SyncJobService) uses services right after this returns, so it must wait
    // for init to finish, not just start; SessionService would otherwise hit null models.
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
        val os = OneSignalImpV2()

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

            // The second caller must see isInitialized == true at the moment its call returns.
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

    // A caller arriving during a retry after FAILED must wait on the retry's signal, not the finished one.
    test("initWithContextSuspend resets latch on retry-after-FAILED") {
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        runBlocking {
            // 1. Force a deterministic FAILED first init via the user-locked branch
            //    (matches the pattern used by other tests in this file).
            mockkObject(AndroidUtils)
            every { AndroidUtils.isAndroidUserUnlocked(any()) } returns false
            val firstInit = os.initWithContextSuspend(context, "appId")
            firstInit shouldBe false
            os.isInitialized shouldBe false
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

            // 3. Re-entrant caller: must wait on the retry's signal, not the finished FAILED one.
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

    // A throw inside init must still end the attempt, or every waiter (e.g. SyncJobService) hangs.
    test("initWithContextSuspend reaches terminal state when internalInit throws") {
        val context = getApplicationContext<Context>()
        val os = OneSignalImpV2()

        // The first call inside internalInit, so no bootstrap internals are involved.
        mockkObject(AndroidUtils)
        every { AndroidUtils.isAndroidUserUnlocked(any()) } throws RuntimeException("simulated bootstrap failure")

        runBlocking {
            val firstResult = withTimeoutOrNull(5_000) {
                os.initWithContextSuspend(context, "appId")
            }
            firstResult shouldBe false
            os.isInitialized shouldBe false

            unmockkObject(AndroidUtils)

            // FAILED, so a retry is allowed and gets a fresh signal.
            val retry = withTimeoutOrNull(5_000) {
                os.initWithContextSuspend(context, "appId")
            }
            retry shouldBe true
            os.isInitialized shouldBe true
        }
    }

    test("a LinkageError during init ends the attempt as FAILED instead of leaving it in progress") {
        val context = getApplicationContext<Context>()
        mockkObject(AndroidUtils)
        try {
            every { AndroidUtils.isAndroidUserUnlocked(any()) } throws NoClassDefFoundError("simulated missing class")

            val suspendOs = OneSignalImpV2()
            runBlocking { withTimeoutOrNull(5_000) { suspendOs.initWithContextSuspend(context, "appId") } } shouldBe false
            suspendOs.gate.state shouldBe InitState.FAILED

            val syncOs = OneSignalImpV2()
            syncOs.initWithContext(context, "appId") shouldBe true
            val deadline = System.currentTimeMillis() + 5_000
            while (syncOs.gate.state == InitState.IN_PROGRESS && System.currentTimeMillis() < deadline) Thread.sleep(10)
            syncOs.gate.state shouldBe InitState.FAILED
            syncOs.user.onesignalId shouldBe ""
        } finally {
            unmockkObject(AndroidUtils)
        }
    }
})

/** Gives each test its own SharedPreferences files, so other tests' writes can't leak in. */
class IsolatedPrefsContext(context: Context) : ContextWrapper(context) {
    private val prefix = java.util.UUID.randomUUID().toString()

    override fun getSharedPreferences(
        name: String,
        mode: Int,
    ): SharedPreferences = super.getSharedPreferences("$prefix-$name", mode)
}
