package com.onesignal.core.internal.application.impl

import android.app.Application
import android.content.Context
import com.onesignal.common.threading.OneSignalDispatchers
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder

/**
 * Verifies ActivityLifecycleInitializer.create() prewarms OneSignalDispatchers at process start,
 * before it registers activity lifecycle callbacks.
 *
 * Intentionally self-contained (does NOT use IOMockHelper). It only needs prewarm() stubbed to a
 * no-op so the real prewarm daemon isn't spawned. It must NOT install IOMockHelper's global
 * launchOn*/suspendify* stubs: this spec sorts lexicographically before sibling specs such as
 * BackgroundManagerTests, and those stubs would leak into the shared OneSignalDispatchers object
 * mock and run dispatched blocks inline in the next spec. Stubbing only prewarm() keeps the leak
 * surface empty.
 */
class ActivityLifecycleInitializerTests : FunSpec({
    beforeTest {
        mockkObject(OneSignalDispatchers)
        every { OneSignalDispatchers.prewarm() } returns Unit
        resetSharedApplicationService()
    }

    afterTest {
        unmockkObject(OneSignalDispatchers)
        resetSharedApplicationService()
    }

    test("create prewarms before registering activity lifecycle callbacks") {
        val application = mockk<Application>(relaxed = true)
        every { application.applicationContext } returns application

        ActivityLifecycleInitializer().create(application)

        verify(exactly = 1) { OneSignalDispatchers.prewarm() }
        verify(exactly = 1) {
            application.registerActivityLifecycleCallbacks(any<Application.ActivityLifecycleCallbacks>())
        }
        verifyOrder {
            OneSignalDispatchers.prewarm()
            application.registerActivityLifecycleCallbacks(any<Application.ActivityLifecycleCallbacks>())
        }
    }

    test("create prewarms even when application context is unavailable") {
        val context = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns context

        ActivityLifecycleInitializer().create(context)

        verify(exactly = 1) { OneSignalDispatchers.prewarm() }
    }
})

private fun resetSharedApplicationService() {
    ApplicationService::class.java.getDeclaredField("sharedInstance").apply {
        isAccessible = true
        set(null, null)
    }
}
