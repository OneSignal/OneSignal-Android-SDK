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
import kotlinx.coroutines.Job

/**
 * Regression coverage for SDK-4505. Asserts that BackgroundManager.onFocus() and
 * onUnfocused() offload their JobScheduler work via suspendifyOnIO instead of
 * running it inline on the calling (Android lifecycle) thread.
 *
 * Mirrors the pattern in ThreadUtilsFeatureFlagTests: mock OneSignalDispatchers
 * and verify it is invoked from the lifecycle entry points when background
 * threading is enabled (which is the configuration the production ANR was
 * reported under).
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

    test("onFocus routes through OneSignalDispatchers.launchOnIO when BACKGROUND_THREADING is on") {
        // Given
        ThreadingMode.useBackgroundThreading = true
        mockkObject(OneSignalDispatchers)
        every { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        val backgroundManager =
            BackgroundManager(
                MockHelper.applicationService(),
                MockHelper.time(0L),
                emptyList(),
            )

        // When
        backgroundManager.onFocus(firedOnSubscribe = false)

        // Then
        verify(exactly = 1) { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) }
    }

    test("onUnfocused routes through OneSignalDispatchers.launchOnIO when BACKGROUND_THREADING is on") {
        // Given
        ThreadingMode.useBackgroundThreading = true
        mockkObject(OneSignalDispatchers)
        every { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        val backgroundManager =
            BackgroundManager(
                MockHelper.applicationService(),
                MockHelper.time(0L),
                emptyList(),
            )

        // When
        backgroundManager.onUnfocused()

        // Then
        verify(exactly = 1) { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) }
    }
})
