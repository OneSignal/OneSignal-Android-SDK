package com.onesignal.location.internal.background

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.location.ILocationManager
import com.onesignal.location.internal.capture.ILocationCapturer
import com.onesignal.location.internal.common.LocationConstants
import com.onesignal.location.internal.preferences.ILocationPreferencesService
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.robolectric.Shadows

@RobolectricTest
class LocationBackgroundServiceTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("backgroundRun will capture current location") {
        // Given
        val mockLocationManager = mockk<ILocationManager>()
        val mockLocationPreferencesService = mockk<ILocationPreferencesService>()
        val mockLocationCapturer = mockk<ILocationCapturer>()
        every { mockLocationCapturer.captureLastLocation() } just runs

        val locationBackgroundService =
            LocationBackgroundService(
                AndroidMockHelper.applicationService(),
                mockLocationManager,
                mockLocationPreferencesService,
                mockLocationCapturer,
                MockHelper.time(1111),
            )

        // When
        locationBackgroundService.backgroundRun()

        // Then
        verify(exactly = 1) { mockLocationCapturer.captureLastLocation() }
    }

    test("scheduleBackgroundRunIn will return null when location services are disabled in SDK") {
        // Given
        val mockLocationManager = mockk<ILocationManager>()
        every { mockLocationManager.isShared } returns false

        val mockLocationPreferencesService = mockk<ILocationPreferencesService>()
        val mockLocationCapturer = mockk<ILocationCapturer>()

        val locationBackgroundService =
            LocationBackgroundService(
                AndroidMockHelper.applicationService(),
                mockLocationManager,
                mockLocationPreferencesService,
                mockLocationCapturer,
                MockHelper.time(1111),
            )

        // When
        val result = locationBackgroundService.scheduleBackgroundRunIn

        // Then
        result shouldBe null
        verify(exactly = 1) { mockLocationManager.isShared }
    }

    test("scheduleBackgroundRunIn will return null when no android permissions") {
        // Given
        val mockLocationManager = mockk<ILocationManager>()
        every { mockLocationManager.isShared } returns true

        val mockLocationPreferencesService = mockk<ILocationPreferencesService>()
        every { mockLocationPreferencesService.lastLocationTime } returns 1111

        val mockLocationCapturer = mockk<ILocationCapturer>()

        val application: Application = ApplicationProvider.getApplicationContext()
        val app = Shadows.shadowOf(application)
        app.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

        val locationBackgroundService =
            LocationBackgroundService(
                AndroidMockHelper.applicationService(),
                mockLocationManager,
                mockLocationPreferencesService,
                mockLocationCapturer,
                MockHelper.time(2222),
            )

        // When
        val result = locationBackgroundService.scheduleBackgroundRunIn

        // Then
        result shouldBe (1000 * LocationConstants.TIME_BACKGROUND_SEC) - (2222 - 1111)
    }
})
