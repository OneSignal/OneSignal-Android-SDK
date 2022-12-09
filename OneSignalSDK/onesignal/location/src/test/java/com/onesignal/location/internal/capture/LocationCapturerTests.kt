package com.onesignal.location.internal.capture

import android.location.Location
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.location.internal.capture.impl.LocationCapturer
import com.onesignal.location.internal.controller.ILocationController
import com.onesignal.location.internal.preferences.ILocationPreferencesService
import com.onesignal.mocks.MockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.junit.runner.RunWith

@RunWith(KotestTestRunner::class)
class LocationCapturerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("captureLastLocation will capture current location with fine") {
        /* Given */
        val mockLocation = mockk<Location>()
        every { mockLocation.accuracy } returns 1111F
        every { mockLocation.time } returns 2222
        every { mockLocation.latitude } returns 8888.1234567
        every { mockLocation.longitude } returns 9999.1234567

        val lastLocationTimeSlot = slot<Long>()
        val mockLocationPreferencesService = mockk<ILocationPreferencesService>()
        every { mockLocationPreferencesService.lastLocationTime = capture(lastLocationTimeSlot) } answers { }

        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockLocationController = mockk<ILocationController>()
        every { mockLocationController.subscribe(any()) } just runs
        every { mockLocationController.getLastLocation() } returns mockLocation

        val mockApplicationService = MockHelper.applicationService()
        every { mockApplicationService.isInForeground } returns true

        val locationCapturer = LocationCapturer(
            mockApplicationService,
            MockHelper.time(1111),
            mockLocationPreferencesService,
            mockPropertiesModelStore,
            mockLocationController
        )

        /* When */
        locationCapturer.captureLastLocation()

        /* Then */
        mockPropertiesModelStore.model.locationAccuracy shouldBe 1111F
        mockPropertiesModelStore.model.locationBackground shouldBe false
        mockPropertiesModelStore.model.locationType shouldBe 1
        mockPropertiesModelStore.model.locationLatitude shouldBe 8888.1234567
        mockPropertiesModelStore.model.locationLongitude shouldBe 9999.1234567
        mockPropertiesModelStore.model.locationTimestamp shouldBe 2222

        lastLocationTimeSlot.captured shouldBe 1111
    }

    test("captureLastLocation will capture current location with coarse") {
        /* Given */
        val mockLocation = mockk<Location>()
        every { mockLocation.accuracy } returns 1111F
        every { mockLocation.time } returns 2222
        every { mockLocation.latitude } returns 8888.123456789
        every { mockLocation.longitude } returns 9999.123456789

        val lastLocationTimeSlot = slot<Long>()
        val mockLocationPreferencesService = mockk<ILocationPreferencesService>()
        every { mockLocationPreferencesService.lastLocationTime = capture(lastLocationTimeSlot) } answers { }

        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockLocationController = mockk<ILocationController>()
        every { mockLocationController.subscribe(any()) } just runs
        every { mockLocationController.getLastLocation() } returns mockLocation

        val mockApplicationService = MockHelper.applicationService()
        every { mockApplicationService.isInForeground } returns true

        val locationCapturer = LocationCapturer(
            mockApplicationService,
            MockHelper.time(1111),
            mockLocationPreferencesService,
            mockPropertiesModelStore,
            mockLocationController
        )

        /* When */
        locationCapturer.locationCoarse = true
        locationCapturer.captureLastLocation()

        /* Then */
        mockPropertiesModelStore.model.locationAccuracy shouldBe 1111F
        mockPropertiesModelStore.model.locationBackground shouldBe false
        mockPropertiesModelStore.model.locationType shouldBe 0
        mockPropertiesModelStore.model.locationLatitude shouldBe 8888.1234568
        mockPropertiesModelStore.model.locationLongitude shouldBe 9999.1234568
        mockPropertiesModelStore.model.locationTimestamp shouldBe 2222

        lastLocationTimeSlot.captured shouldBe 1111
    }

    test("captureLastLocation will not capture current location when not available") {
        /* Given */
        val lastLocationTimeSlot = slot<Long>()
        val mockLocationPreferencesService = mockk<ILocationPreferencesService>()
        every { mockLocationPreferencesService.lastLocationTime = capture(lastLocationTimeSlot) } answers { }

        val mockPropertiesModelStore = MockHelper.propertiesModelStore()
        val mockLocationController = mockk<ILocationController>()
        every { mockLocationController.subscribe(any()) } just runs
        every { mockLocationController.getLastLocation() } returns null

        val mockApplicationService = MockHelper.applicationService()

        val locationCapturer = LocationCapturer(
            mockApplicationService,
            MockHelper.time(1111),
            mockLocationPreferencesService,
            mockPropertiesModelStore,
            mockLocationController
        )

        /* When */
        locationCapturer.captureLastLocation()

        /* Then */
        mockPropertiesModelStore.model.locationAccuracy shouldBe null
        mockPropertiesModelStore.model.locationBackground shouldBe null
        mockPropertiesModelStore.model.locationType shouldBe null
        mockPropertiesModelStore.model.locationLatitude shouldBe null
        mockPropertiesModelStore.model.locationLongitude shouldBe null
        mockPropertiesModelStore.model.locationTimestamp shouldBe null

        lastLocationTimeSlot.captured shouldBe 1111
    }
})
