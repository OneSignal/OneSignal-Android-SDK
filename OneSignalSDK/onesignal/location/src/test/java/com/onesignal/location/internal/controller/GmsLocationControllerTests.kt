package com.onesignal.location.internal.controller

import android.location.Location
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.location.internal.controller.impl.GmsLocationController
import com.onesignal.location.shadows.ShadowFusedLocationProviderApi
import com.onesignal.location.shadows.ShadowGoogleApiClient
import com.onesignal.location.shadows.ShadowGoogleApiClientBuilder
import com.onesignal.mocks.AndroidMockHelper
import com.onesignal.notifications.extensions.RobolectricTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.runner.junit4.KotestTestRunner
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@Config(
    packageName = "com.onesignal.example",
    shadows = [ShadowGoogleApiClientBuilder::class],
    sdk = [26],
)
@RobolectricTest
@RunWith(KotestTestRunner::class)
class GmsLocationControllerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("start will connect and fire locationChanged with location") {
        // Given
        val location = Location("TEST_PROVIDER")
        location.latitude = 123.45
        location.longitude = 678.91

        ShadowFusedLocationProviderApi.injectToLocationServices(listOf(location))
        val applicationService = AndroidMockHelper.applicationService()
        every { applicationService.isInForeground } returns true
        val gmsLocationController = GmsLocationController(applicationService)

        val spyLocationUpdateHandler = spyk<ILocationUpdatedHandler>()
        gmsLocationController.subscribe(spyLocationUpdateHandler)

        // When
        val response = gmsLocationController.start()

        // Then
        response shouldBe true
        ShadowGoogleApiClient.connected shouldBe true
        verify(exactly = 1) {
            spyLocationUpdateHandler.onLocationChanged(
                withArg {
                    it.latitude shouldBe 123.45
                    it.longitude shouldBe 678.91
                },
            )
        }
    }

    test("start twice will return the initial location") {
        // Given
        val location1 = Location("TEST_PROVIDER")
        location1.latitude = 123.45
        location1.longitude = 678.91

        val location2 = Location("TEST_PROVIDER")
        location2.latitude = 678.91
        location2.longitude = 123.45

        ShadowFusedLocationProviderApi.injectToLocationServices(listOf(location1, location2))
        val applicationService = AndroidMockHelper.applicationService()
        every { applicationService.isInForeground } returns true
        val gmsLocationController = GmsLocationController(applicationService)

        val spyLocationUpdateHandler = spyk<ILocationUpdatedHandler>()
        gmsLocationController.subscribe(spyLocationUpdateHandler)

        // When
        val response1 = gmsLocationController.start()
        val response2 = gmsLocationController.start()

        // Then
        response1 shouldBe true
        response2 shouldBe true
        ShadowGoogleApiClient.connected shouldBe true
        verifySequence {
            spyLocationUpdateHandler.onLocationChanged(
                withArg {
                    it.latitude shouldBe 123.45
                    it.longitude shouldBe 678.91
                },
            )
            spyLocationUpdateHandler.onLocationChanged(
                withArg {
                    it.latitude shouldBe 123.45
                    it.longitude shouldBe 678.91
                },
            )
        }
    }

    test("getLastLocation will retrieve a new location") {
        // Given
        val location1 = Location("TEST_PROVIDER")
        location1.latitude = 123.45
        location1.longitude = 678.91

        val location2 = Location("TEST_PROVIDER")
        location2.latitude = 678.91
        location2.longitude = 123.45

        ShadowFusedLocationProviderApi.injectToLocationServices(listOf(location1, location2))
        val applicationService = AndroidMockHelper.applicationService()
        every { applicationService.isInForeground } returns true
        val gmsLocationController = GmsLocationController(applicationService)

        val spyLocationUpdateHandler = spyk<ILocationUpdatedHandler>()
        gmsLocationController.subscribe(spyLocationUpdateHandler)

        // When
        val response = gmsLocationController.start()
        val lastLocation = gmsLocationController.getLastLocation()

        // Then
        response shouldBe true
        lastLocation shouldNotBe null
        lastLocation!!.latitude shouldBe 678.91
        lastLocation.longitude shouldBe 123.45
        ShadowGoogleApiClient.connected shouldBe true
        verifySequence {
            spyLocationUpdateHandler.onLocationChanged(
                withArg {
                    it.latitude shouldBe 123.45
                    it.longitude shouldBe 678.91
                },
            )
        }
    }

    test("stop will disconnect") {
        // Given
        val location1 = Location("TEST_PROVIDER")
        location1.latitude = 123.45
        location1.longitude = 678.91

        val location2 = Location("TEST_PROVIDER")
        location2.latitude = 678.91
        location2.longitude = 123.45

        ShadowFusedLocationProviderApi.injectToLocationServices(listOf(location1, location2))
        val applicationService = AndroidMockHelper.applicationService()
        every { applicationService.isInForeground } returns true
        val gmsLocationController = GmsLocationController(applicationService)

        val spyLocationUpdateHandler = spyk<ILocationUpdatedHandler>()
        gmsLocationController.subscribe(spyLocationUpdateHandler)

        // When
        val response = gmsLocationController.start()
        gmsLocationController.stop()

        // Then
        response shouldBe true
        ShadowGoogleApiClient.connected shouldBe false
        verifySequence {
            spyLocationUpdateHandler.onLocationChanged(
                withArg {
                    it.latitude shouldBe 123.45
                    it.longitude shouldBe 678.91
                },
            )
        }
    }
})
