package com.onesignal.core.internal.application.impl

import android.app.Application
import android.content.Context
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.mocks.IOMockHelper
import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder

class ActivityLifecycleInitializerTests : FunSpec({
    listener(IOMockHelper)

    beforeAny {
        // IOMockHelper owns the OneSignalDispatchers object mock and stubs prewarm() to a no-op.
        // Clear only recorded calls so this spec verifies initializer ordering without spawning the
        // real OneSignal-prewarm daemon thread.
        clearMocks(OneSignalDispatchers, answers = false)
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
