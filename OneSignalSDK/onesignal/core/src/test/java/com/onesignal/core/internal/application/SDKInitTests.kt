package com.onesignal.core.internal.application

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.threading.LatchAwaiter
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.internal.OneSignalImp
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking

@RobolectricTest
class SDKInitTests : FunSpec({

    beforeAny {
        val context = getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("OneSignal", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        Logging.logLevel = LogLevel.NONE
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

    test("initWithContext with no appId blocks and will return false") {
        // Given
        // block SharedPreference before calling init
        val trigger = LatchAwaiter("Test")
        val context = getApplicationContext<Context>()
        val blockingPrefContext = BlockingPrefsContext(context, trigger, 2000)
        val os = OneSignalImp()
        var initSuccess = true

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
        trigger.release()

        accessorThread.join(500)
        accessorThread.isAlive shouldBe false

        // always return false because appId is missing
        initSuccess shouldBe false
        os.isInitialized shouldBe false
    }

    test("initWithContext with appId does not block") {
        // Given
        // block SharedPreference before calling init
        val trigger = LatchAwaiter("Test")
        val context = getApplicationContext<Context>()
        val blockingPrefContext = BlockingPrefsContext(context, trigger, 1000)
        val os = OneSignalImp()

        // When
        val accessorThread =
            Thread {
                os.initWithContext(blockingPrefContext, "appId")
            }

        accessorThread.start()
        accessorThread.join(500)

        // Then
        // should complete even SharedPreferences is unavailable
        accessorThread.isAlive shouldBe false
        os.isInitialized shouldBe true
    }

    test("accessors will be blocked if call too early after initWithContext with appId") {
        // Given
        // block SharedPreference before calling init
        val trigger = LatchAwaiter("Test")
        val context = getApplicationContext<Context>()
        val blockingPrefContext = BlockingPrefsContext(context, trigger, 2000)
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
        trigger.release()

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
        val trigger = LatchAwaiter("Test")
        val context = getApplicationContext<Context>()
        val blockingPrefContext = BlockingPrefsContext(context, trigger, 2000)
        val os = OneSignalImp()
        val externalId = "testUser"

        val accessorThread =
            Thread {
                os.initWithContext(blockingPrefContext, "appId")
                os.login(externalId)
            }

        accessorThread.start()
        accessorThread.join(500)

        os.isInitialized shouldBe true
        accessorThread.isAlive shouldBe true

        // release the lock on SharedPreferences
        trigger.release()

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

    test("externalId retrieved correctly when login right after init") {
        // Given
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()
        val testExternalId = "testUser"

        // When
        os.initWithContext(context, "appId")
        val oldExternalId = os.user.externalId
        os.login(testExternalId)
        val newExternalId = os.user.externalId

        oldExternalId shouldBe ""
        newExternalId shouldBe testExternalId
    }

    test("accessor instances after multiple initWithContext calls are consistent") {
        // Given
        val context = getApplicationContext<Context>()
        val os = OneSignalImp()

        // When
        os.initWithContext(context, "appId")
        val oldUser = os.user

        // Second init from some internal class
        os.initWithContext(context)
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
        initialExternalId shouldBe ""

        // login
        os.login(testExternalId)
        os.user.externalId shouldBe testExternalId

        // addTags and getTags
        os.user.addTags(tags)
        val retrievedTags = os.user.getTags()
        retrievedTags shouldContain ("test" to "integration")
        retrievedTags shouldContain ("version" to "1.0")

        // logout
        os.logout()
        os.user.externalId shouldBe ""
    }
})

/**
 * Simulate a context awaiting for a shared preference until the trigger is signaled
 */
class BlockingPrefsContext(
    context: Context,
    private val unblockTrigger: LatchAwaiter,
    private val timeoutInMillis: Long,
) : ContextWrapper(context) {
    override fun getSharedPreferences(
        name: String,
        mode: Int,
    ): SharedPreferences {
        try {
            unblockTrigger.await(timeoutInMillis)
        } catch (e: InterruptedException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            throw e
        }

        return super.getSharedPreferences(name, mode)
    }
}
