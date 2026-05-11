package com.onesignal.core.internal.background.impl

import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.ThreadingMode
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
 * Regression coverage for SDK-4505. Asserts that BackgroundManager.onFocus() and
 * onUnfocused() offload their JobScheduler work via suspendifyOnSerialIO so that
 *  1. the JobScheduler Binder call doesn't run inline on the main thread (the ANR), and
 *  2. rapid foreground/background switches are processed in submission order (the
 *     refinement on top of the original PR — a multi-threaded IO pool could reorder
 *     a `schedule` after a `cancel`, leaving the SDK in the opposite of the requested
 *     state).
 *
 * Mirrors the pattern in ThreadUtilsFeatureFlagTests: mock OneSignalDispatchers and
 * verify launchOnSerialIO is invoked from the lifecycle entry points when background
 * threading is enabled (the configuration the production ANR was reported under).
 */
class BackgroundManagerTests : FunSpec({

    beforeEach {
        Logging.logLevel = LogLevel.NONE
        ThreadingMode.useBackgroundThreading = false
    }

    afterEach {
        unmockkObject(OneSignalDispatchers)
        ThreadingMode.useBackgroundThreading = false
    }

    test("onFocus routes through OneSignalDispatchers.launchOnSerialIO when BACKGROUND_THREADING is on") {
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

    test("onUnfocused routes through OneSignalDispatchers.launchOnSerialIO when BACKGROUND_THREADING is on") {
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

    test("rapid unfocus -> focus burst submits both events to the serial dispatcher in order") {
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
})
