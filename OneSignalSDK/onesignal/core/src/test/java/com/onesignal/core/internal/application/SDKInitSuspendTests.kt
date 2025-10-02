package com.onesignal.core.internal.application

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withContext

/**
 * Integration tests for the suspend-based OneSignal API
 *
 * These tests verify real behavior:
 * - State changes (login/logout affect user ID)
 * - Threading (methods run on background threads)
 * - Initialization dependencies (services require init)
 * - Coroutine behavior (proper suspend/resume)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RobolectricTest
class SDKInitSuspendTests : FunSpec({

    val testAppId = "test-app-id-123"

    beforeEach {
        Logging.logLevel = LogLevel.NONE
    }

    afterEach {
        val context = getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("OneSignal", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    test("suspend login changes user external ID") {
        // Given
        val context = getApplicationContext<Context>()
        val testDispatcher = UnconfinedTestDispatcher()
        val os = TestOneSignalImp(testDispatcher)
        val testExternalId = "test-user-123"

        runBlocking {
            // When
            os.login(context, testAppId, testExternalId)

            // Then - verify state actually changed
            os.getCurrentExternalId() shouldBe testExternalId
            os.getLoginCount() shouldBe 1
            os.getUser().externalId shouldBe testExternalId
        }
    }

    test("suspend logout clears user external ID") {
        // Given
        val context = getApplicationContext<Context>()
        val testDispatcher = UnconfinedTestDispatcher()
        val os = TestOneSignalImp(testDispatcher)

        runBlocking {
            // Setup - login first
            os.login(context, testAppId, "initial-user")
            os.getCurrentExternalId() shouldBe "initial-user"

            // When
            os.logout(context, testAppId)

            // Then - verify state was cleared
            os.getCurrentExternalId() shouldBe ""
            os.getLogoutCount() shouldBe 1
            os.getUser().externalId shouldBe ""
        }
    }

    test("suspend accessors require initialization") {
        // Given
        val testDispatcher = UnconfinedTestDispatcher()
        val os = TestOneSignalImp(testDispatcher)

        runBlocking {
            // When/Then - accessing services before init should fail
            shouldThrow<IllegalStateException> {
                os.getUser()
            }

            shouldThrow<IllegalStateException> {
                os.getSession()
            }

            shouldThrow<IllegalStateException> {
                os.getNotifications()
            }
        }
    }

    test("suspend accessors work after initialization") {
        // Given
        val context = getApplicationContext<Context>()
        val testDispatcher = UnconfinedTestDispatcher()
        val os = TestOneSignalImp(testDispatcher)

        runBlocking {
            // When
            os.initWithContext(context, "test-app-id")

            // Then - services should be accessible
            val user = os.getUser()
            val session = os.getSession()
            val notifications = os.getNotifications()
            val inAppMessages = os.getInAppMessages()
            val location = os.getLocation()

            user shouldNotBe null
            session shouldNotBe null
            notifications shouldNotBe null
            inAppMessages shouldNotBe null
            location shouldNotBe null

            os.getInitializationCount() shouldBe 1
        }
    }

    test("suspend methods run on background thread") {
        // Given
        val context = getApplicationContext<Context>()
        val testDispatcher = UnconfinedTestDispatcher()
        val os = TestOneSignalImp(testDispatcher)

        runBlocking {
            val mainThreadName = Thread.currentThread().name

            // When - call suspend method and capture thread info
            var backgroundThreadName: String? = null

            os.initWithContext(context, "test-app-id")

            withContext(Dispatchers.IO) {
                backgroundThreadName = Thread.currentThread().name
                os.login(context, testAppId, "thread-test-user")
            }

            // Then - verify it ran on different thread
            backgroundThreadName shouldNotBe mainThreadName
            os.getCurrentExternalId() shouldBe "thread-test-user"
        }
    }

    test("multiple sequential suspend calls work correctly") {
        // Given
        val context = getApplicationContext<Context>()
        val testDispatcher = UnconfinedTestDispatcher()
        val os = TestOneSignalImp(testDispatcher)

        runBlocking {
            // When - run operations sequentially (not concurrently to avoid race conditions)
            os.initWithContext(context, "sequential-app-id")
            os.login(context, testAppId, "user1")
            val user1Id = os.getCurrentExternalId()

            os.logout(context, testAppId)
            val loggedOutId = os.getCurrentExternalId()

            os.login(context, testAppId, "final-user")
            val finalId = os.getCurrentExternalId()

            // Then - verify each step worked correctly
            user1Id shouldBe "user1"
            loggedOutId shouldBe ""
            finalId shouldBe "final-user"

            os.getInitializationCount() shouldBe 1 // Only initialized once
            os.getLoginCount() shouldBe 2
            os.getLogoutCount() shouldBe 1
        }
    }

    test("login and logout auto-initialize when needed") {
        // Given
        val context = getApplicationContext<Context>()
        val testDispatcher = UnconfinedTestDispatcher()
        val os = TestOneSignalImp(testDispatcher)

        runBlocking {
            // When - call login without explicit init
            os.login(context, testAppId, "auto-init-user")

            // Then - should auto-initialize and work
            os.isInitialized shouldBe true
            os.getCurrentExternalId() shouldBe "auto-init-user"
            os.getInitializationCount() shouldBe 1 // auto-initialized
            os.getLoginCount() shouldBe 1

            // When - call logout (should not double-initialize)
            os.logout(context, testAppId)

            // Then
            os.getCurrentExternalId() shouldBe ""
            os.getInitializationCount() shouldBe 1 // still just 1
            os.getLogoutCount() shouldBe 1
        }
    }
})
