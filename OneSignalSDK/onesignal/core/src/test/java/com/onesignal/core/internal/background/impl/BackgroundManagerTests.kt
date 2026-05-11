package com.onesignal.core.internal.background.impl

import android.app.job.JobScheduler
import android.content.Context
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.ThreadingMode
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Job

/**
 * Regression coverage for SDK-4505. Asserts the two-pronged behavior of
 * BackgroundManager.onFocus / onUnfocused:
 *
 *   FF on  (sdk_background_threading enabled)
 *     -> route through OneSignalDispatchers.launchOnSerialIO so the
 *        JobScheduler Binder call doesn't run inline on the main thread
 *        (the ANR fix), and rapid bursts stay in submission order (the
 *        serial-dispatcher refinement).
 *
 *   FF off
 *     -> legacy inline path. cancelSyncTask / scheduleBackground execute
 *        on the calling thread; no dispatcher is involved. This is the
 *        control cohort for the gated rollout.
 */
class BackgroundManagerTests : FunSpec({

    fun applicationServiceWithStubbedJobScheduler(): IApplicationService {
        val appService = MockHelper.applicationService()
        val context = mockk<Context>(relaxed = true)
        val jobScheduler = mockk<JobScheduler>(relaxed = true)
        every { appService.appContext } returns context
        every { context.getSystemService(Context.JOB_SCHEDULER_SERVICE) } returns jobScheduler
        every { jobScheduler.allPendingJobs } returns emptyList()
        return appService
    }

    beforeEach {
        Logging.logLevel = LogLevel.NONE
        ThreadingMode.useBackgroundThreading = false
    }

    afterEach {
        unmockkObject(OneSignalDispatchers)
        ThreadingMode.useBackgroundThreading = false
    }

    test("FF on: onFocus routes through OneSignalDispatchers.launchOnSerialIO") {
        ThreadingMode.useBackgroundThreading = true
        mockkObject(OneSignalDispatchers)
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        val backgroundManager =
            BackgroundManager(
                MockHelper.applicationService(),
                MockHelper.time(0L),
                emptyList(),
            )

        backgroundManager.onFocus(firedOnSubscribe = false)

        verify(exactly = 1) { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) }
    }

    test("FF on: onUnfocused routes through OneSignalDispatchers.launchOnSerialIO") {
        ThreadingMode.useBackgroundThreading = true
        mockkObject(OneSignalDispatchers)
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        val backgroundManager =
            BackgroundManager(
                MockHelper.applicationService(),
                MockHelper.time(0L),
                emptyList(),
            )

        backgroundManager.onUnfocused()

        verify(exactly = 1) { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) }
    }

    test("FF on: rapid unfocus -> focus burst submits both events to the serial dispatcher in order") {
        ThreadingMode.useBackgroundThreading = true
        mockkObject(OneSignalDispatchers)
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        val backgroundManager =
            BackgroundManager(
                MockHelper.applicationService(),
                MockHelper.time(0L),
                emptyList(),
            )

        // Simulate the user backgrounding then immediately foregrounding the app on the
        // main thread (the SDK-4505 reproducer). Both calls must route through the same
        // serial dispatcher so the IO worker observes them in this submission order.
        backgroundManager.onUnfocused()
        backgroundManager.onFocus(firedOnSubscribe = false)

        verify(exactly = 2) { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) }
        verifyOrder {
            OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>())
            OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>())
        }
    }

    test("FF off: onFocus runs inline and does NOT dispatch to the serial dispatcher") {
        ThreadingMode.useBackgroundThreading = false
        mockkObject(OneSignalDispatchers)
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        val backgroundManager =
            BackgroundManager(
                applicationServiceWithStubbedJobScheduler(),
                MockHelper.time(0L),
                emptyList(),
            )

        backgroundManager.onFocus(firedOnSubscribe = false)

        verify(exactly = 0) { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) }
    }

    test("FF off: onUnfocused runs inline and does NOT dispatch to the serial dispatcher") {
        ThreadingMode.useBackgroundThreading = false
        mockkObject(OneSignalDispatchers)
        every { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        val backgroundManager =
            BackgroundManager(
                applicationServiceWithStubbedJobScheduler(),
                MockHelper.time(0L),
                emptyList(),
            )

        backgroundManager.onUnfocused()

        verify(exactly = 0) { OneSignalDispatchers.launchOnSerialIO(any<suspend () -> Unit>()) }
    }
})
