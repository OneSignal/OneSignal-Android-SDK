package com.onesignal.sdktest.application

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.OneSignal
import com.onesignal.user.internal.identity.IdentityModelStore
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RobolectricTest
@Config(application = TestApplication::class, sdk = [33])
class DemoAppTests : FunSpec({

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
        val trigger = CountDownLatch(1)
        val context = getApplicationContext<TestApplication>()
        val blockingPrefContext = BlockingPrefsContext(context, trigger)

        // When
        val exec = Executors.newSingleThreadExecutor()
        val future = exec.submit { OneSignal.initWithContext(blockingPrefContext, "appId") }

        // Then
        future.isDone.shouldBeTrue()

        // now release the SharedPreferences
        trigger.countDown()
        OneSignal.isInitialized shouldBe true

        future.cancel(true)
        exec.shutdownNow()
    }

    test("ensure adding tags right after initWithContext is successful") {
        // Given
        val context = getApplicationContext<TestApplication>()
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
        val context = getApplicationContext<TestApplication>()

        // When
        OneSignal.initWithContext(context, "appId")
        val onesignalId = OneSignal.getService<IdentityModelStore>().model.onesignalId

        // Then
        onesignalId shouldStartWith "local"
    }

    test("a push subscription should be created right after initWithContext") {
        // Given
        val context = getApplicationContext<TestApplication>()
        OneSignal.initWithContext(context, "appId")

        // When
        val pushSub = OneSignal.User.pushSubscription

        // Then
        pushSub shouldNotBe null
    }

    test("externalId retrieved correctly when login right after init") {
        val context = getApplicationContext<TestApplication>()
        OneSignal.initWithContext(context, "appId")
        val testExternalId = "testUser"

        // When
        val oldExternalId = OneSignal.User.externalId
        OneSignal.login(testExternalId)
        val newExternalId = OneSignal.User.externalId

        oldExternalId shouldBe ""
        newExternalId shouldBe testExternalId
    }
})

/**
 * Simulate a context awaiting for a shared preference until the trigger is signaled
 */
class BlockingPrefsContext(
    context: Context,
    private val unblockTrigger: CountDownLatch
) : ContextWrapper(context) {
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        try {
            unblockTrigger.await(10000, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            throw e
        }

        return super.getSharedPreferences(name, mode)
    }
}
