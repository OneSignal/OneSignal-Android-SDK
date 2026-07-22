package com.onesignal.notifications.activities

import android.content.Context
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.NotificationOpenedActivityHMS
import com.onesignal.OneSignal
import com.onesignal.common.services.IServiceProvider
import com.onesignal.common.threading.suspendifyOnDefault
import com.onesignal.mocks.IOMockHelper
import com.onesignal.notifications.internal.open.INotificationOpenedProcessorHMS
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.robolectric.Robolectric

/**
 * Regression test for SDK-4734: the HMS notification-open trampoline must not be finished until
 * after [INotificationOpenedProcessorHMS.handleHMSNotificationOpenIntent] has run (which sets
 * entryState to NOTIFICATION_CLICK). Finishing synchronously before the background work let the
 * trampoline's onStop reach ApplicationService while entryState was still stale, skipping the
 * stale-entry reset and mis-attributing the next organic launch as a direct notification session.
 *
 * suspendifyOnDefault is stubbed to capture (not run) its block so the test faithfully reproduces
 * the async gap: the synchronous part of processIntent runs first, then the coroutine body runs.
 */
@RobolectricTest
class NotificationOpenedActivityHMSTest : FunSpec({
    listener(IOMockHelper)

    lateinit var serviceProvider: IServiceProvider
    lateinit var processor: INotificationOpenedProcessorHMS

    beforeAny {
        processor = mockk(relaxed = true)
        serviceProvider = mockk(relaxed = true)
        every { serviceProvider.getService(INotificationOpenedProcessorHMS::class.java) } returns processor

        mockkObject(OneSignal)
        every { OneSignal.services } returns serviceProvider
        coEvery { OneSignal.initWithContext(any<Context>()) } returns true
    }

    afterAny {
        unmockkObject(OneSignal)
    }

    test("does not finish the HMS trampoline until after HMS open processing runs") {
        var capturedBlock: (suspend () -> Unit)? = null
        every { suspendifyOnDefault(any()) } answers { capturedBlock = firstArg() }

        val activity = Robolectric.buildActivity(NotificationOpenedActivityHMS::class.java).setup().get()

        // Regression assertion: the synchronous part of processIntent must NOT finish the activity
        // and must NOT have run HMS processing yet. The buggy version called finish() here.
        activity.isFinishing shouldBe false
        coVerify(exactly = 0) { processor.handleHMSNotificationOpenIntent(any(), any()) }

        runBlocking { capturedBlock!!.invoke() }

        coVerify(exactly = 1) { processor.handleHMSNotificationOpenIntent(activity, any()) }
        activity.isFinishing shouldBe true
    }

    test("always finishes the HMS trampoline even when initialization fails") {
        coEvery { OneSignal.initWithContext(any<Context>()) } returns false

        var capturedBlock: (suspend () -> Unit)? = null
        every { suspendifyOnDefault(any()) } answers { capturedBlock = firstArg() }

        val activity = Robolectric.buildActivity(NotificationOpenedActivityHMS::class.java).setup().get()

        runBlocking { capturedBlock!!.invoke() }

        coVerify(exactly = 0) { processor.handleHMSNotificationOpenIntent(any(), any()) }
        activity.isFinishing shouldBe true
    }
})
