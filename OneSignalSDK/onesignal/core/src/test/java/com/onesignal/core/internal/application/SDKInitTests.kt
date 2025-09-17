package com.onesignal.core.internal.application

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.OneSignal
import com.onesignal.common.threading.LatchAwaiter
import com.onesignal.internal.OneSignalImp
import com.onesignal.user.internal.identity.IdentityModelStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.throwables.shouldThrowUnit
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import java.util.concurrent.Executors

@RobolectricTest
class SDKInitTests: FunSpec({
    IsolationMode.InstancePerLeaf

    test("ensure OneSignal accessors should throw before calling initWithContext") {
        shouldThrow<IllegalStateException> {
            OneSignal.User
        }
        shouldThrow<IllegalStateException> {
            OneSignal.InAppMessages
        }
        shouldThrow<IllegalStateException> {
            OneSignal.Session
        }
        shouldThrow<IllegalStateException> {
            OneSignal.Notifications
        }
        shouldThrow<IllegalStateException> {
            OneSignal.Location
        }
    }

    test("init will not block when no other accessor is called after initWithContext") {
        // Given
        // block SharedPreference before calling init
        val trigger = LatchAwaiter("Test")
        val context =  getApplicationContext<Context>()
        val blockingPrefContext = BlockingPrefsContext(context, trigger)

        // When
        val exec = Executors.newSingleThreadExecutor()
        val future = exec.submit { OneSignal.initWithContext(blockingPrefContext, "appId") }

        // Then


        // now release the SharedPreferences
        trigger.completeSuccess()
        future.isDone.shouldBeTrue()

        OneSignal.isInitialized shouldBe true

        future.cancel(true)
        exec.shutdownNow()
    }

    test("ensure adding tags right after initWithContext is successful") {
        // Given
        val context = getApplicationContext<Context>()
        val tagKey = "tagKey"
        val tagValue = "tagValue"
        val testTags = mapOf(tagKey to tagValue)

        // When
        OneSignal.initWithContext(context, "appId")

        OneSignal.User.addTags(testTags)

        // Then
        val tags = OneSignal.User.getTags()
        tags shouldContain (tagKey to tagValue)
    }

    test("onesignalId requested right after initWithContext should be local") {
        // Given
        val context = getApplicationContext<Context>()

        // When
        OneSignal.initWithContext(context, "appId")
        val onesignalId = OneSignal.getService<IdentityModelStore>().model.onesignalId

        // Then
        onesignalId shouldStartWith "local"
    }

    test("a push subscription should be created right after initWithContext") {
        // Given
        val context = getApplicationContext<Context>()
        OneSignal.initWithContext(context, "appId")

        // When
        val pushSub = OneSignal.User.pushSubscription

        // Then
        pushSub shouldNotBe null
    }

    test("externalId retrieved correctly when login right after init") {
        val context = getApplicationContext<Context>()
        OneSignal.initWithContext(context, "appId")
        val testExternalId = "testUser"

        // When
        val oldExternalId = OneSignal.User.externalId
        OneSignal.login(testExternalId)
        val newExternalId = OneSignal.User.externalId

        oldExternalId shouldBe ""
        newExternalId shouldBe testExternalId
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
})

/**
 * Simulate a context awaiting for a shared preference until the trigger is signaled
 */
class BlockingPrefsContext(
    context: Context,
    private val unblockTrigger: LatchAwaiter
) : ContextWrapper(context) {
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        try {
            unblockTrigger.waitForCompletion(10000)
        } catch (e: InterruptedException) {
            throw e
        }

        return super.getSharedPreferences(name, mode)
    }
}