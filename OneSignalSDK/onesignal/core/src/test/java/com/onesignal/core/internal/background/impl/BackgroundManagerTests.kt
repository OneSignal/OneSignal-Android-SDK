package com.onesignal.core.internal.background.impl

import com.onesignal.common.threading.OneSignalDispatchers
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
 * Regression coverage for SDK-4505. Asserts that BackgroundManager.onFocus / onUnfocused
 * route through OneSignalDispatchers.launchOnSerialIO so the JobScheduler Binder call doesn't
 * run inline on the main thread (the ANR fix), and rapid bursts stay in submission order
 * (the serial-dispatcher refinement).
 */
class BackgroundManagerTests : FunSpec({

    beforeEach {
        Logging.logLevel = LogLevel.NONE
    }

    afterEach {
        unmockkObject(OneSignalDispatchers)
    }

    test("onFocus routes through OneSignalDispatchers.launchOnSerialIO") {
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

    test("onUnfocused routes through OneSignalDispatchers.launchOnSerialIO") {
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
