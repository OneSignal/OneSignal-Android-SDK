package com.onesignal.location.internal.permissions

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.activities.PermissionsActivity
import com.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.permissions.AlertDialogPrepromptForAndroidSettings
import com.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.AndroidMockHelper
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.delay

@RobolectricTest
class LocationPermissionControllerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("prompt will return true once permission is accepted by user") {
        // Given
        val mockRequestPermissionService = mockk<IRequestPermissionService>()

        val locationPermissionController =
            LocationPermissionController(
                mockRequestPermissionService,
                AndroidMockHelper.applicationService(),
            )

        every { mockRequestPermissionService.startPrompt(any(), any(), any(), any()) } coAnswers {
            delay(1000)
            locationPermissionController.onAccept()
        }

        // When
        val beforeTime = System.currentTimeMillis()
        val response = locationPermissionController.prompt(false, "permission")
        val afterTime = System.currentTimeMillis()

        val deltaTime = afterTime - beforeTime

        // Then
        response shouldBe true
        deltaTime shouldBeGreaterThan 1000
        verify(exactly = 1) { mockRequestPermissionService.startPrompt(false, any(), "permission", any()) }
    }

    test("prompt will return false once permission is rejected by user") {
        // Given
        val mockRequestPermissionService = mockk<IRequestPermissionService>()

        val locationPermissionController =
            LocationPermissionController(
                mockRequestPermissionService,
                AndroidMockHelper.applicationService(),
            )

        every { mockRequestPermissionService.startPrompt(any(), any(), any(), any()) } coAnswers {
            delay(1000)
            locationPermissionController.onReject(false)
        }

        // When
        val beforeTime = System.currentTimeMillis()
        val response = locationPermissionController.prompt(false, "permission")
        val afterTime = System.currentTimeMillis()

        val deltaTime = afterTime - beforeTime

        // Then
        response shouldBe false
        deltaTime shouldBeGreaterThan 1000
        verify(exactly = 1) { mockRequestPermissionService.startPrompt(false, any(), "permission", any()) }
    }

    test("prompt will notify subscribers as accepted once permission is accepted by user") {
        // Given
        val mockRequestPermissionService = mockk<IRequestPermissionService>()

        val locationPermissionController =
            LocationPermissionController(
                mockRequestPermissionService,
                AndroidMockHelper.applicationService(),
            )

        every { mockRequestPermissionService.startPrompt(any(), any(), any(), any()) } coAnswers {
            locationPermissionController.onAccept()
        }
        val spyLocationPermissionChangedHandler = spyk<ILocationPermissionChangedHandler>()

        // When
        locationPermissionController.subscribe(spyLocationPermissionChangedHandler)
        locationPermissionController.prompt(false, "permission")

        // Then
        verify(exactly = 1) { spyLocationPermissionChangedHandler.onLocationPermissionChanged(true) }
    }

    test("prompt will notify subscribers as rejected once permission is rejected by user") {
        // Given
        val mockRequestPermissionService = mockk<IRequestPermissionService>()

        val locationPermissionController =
            LocationPermissionController(
                mockRequestPermissionService,
                AndroidMockHelper.applicationService(),
            )

        every { mockRequestPermissionService.startPrompt(any(), any(), any(), any()) } coAnswers {
            locationPermissionController.onReject(false)
        }
        val spyLocationPermissionChangedHandler = spyk<ILocationPermissionChangedHandler>()

        // When
        locationPermissionController.subscribe(spyLocationPermissionChangedHandler)
        locationPermissionController.prompt(false, "permission")

        // Then
        verify(exactly = 1) { spyLocationPermissionChangedHandler.onLocationPermissionChanged(false) }
    }

    test("onReject with fallback waits for host activity before showing settings dialog") {
        mockkObject(AlertDialogPrepromptForAndroidSettings)

        try {
            val mockRequestPermissionService = mockk<IRequestPermissionService>()
            val activityHandlers = mutableListOf<IActivityLifecycleHandler>()
            val mockAppService = mockk<IApplicationService>()
            val permissionsActivity = mockk<PermissionsActivity>(relaxed = true)
            val hostActivity = mockk<Activity>(relaxed = true)
            val callbackSlot = slot<AlertDialogPrepromptForAndroidSettings.Callback>()

            every { hostActivity.getString(any()) } returns "Location"
            every { mockAppService.current } returns permissionsActivity
            every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()
            every { mockAppService.addActivityLifecycleHandler(any()) } answers {
                activityHandlers.add(firstArg<IActivityLifecycleHandler>())
            }
            every { mockAppService.removeActivityLifecycleHandler(any()) } just runs
            every { mockAppService.addApplicationLifecycleHandler(any()) } just runs
            every { mockAppService.removeApplicationLifecycleHandler(any()) } just runs
            every {
                AlertDialogPrepromptForAndroidSettings.show(
                    hostActivity,
                    any(),
                    any(),
                    capture(callbackSlot),
                )
            } just runs

            var locationPermissionChanged: Boolean? = null
            val locationPermissionController =
                LocationPermissionController(
                    mockRequestPermissionService,
                    mockAppService,
                )
            locationPermissionController.subscribe(
                object : ILocationPermissionChangedHandler {
                    override fun onLocationPermissionChanged(enabled: Boolean) {
                        locationPermissionChanged = enabled
                    }
                },
            )

            locationPermissionController.onReject(true)

            verify(exactly = 0) {
                AlertDialogPrepromptForAndroidSettings.show(any<Activity>(), any(), any(), any())
            }
            activityHandlers.last().onActivityAvailable(permissionsActivity)
            verify(exactly = 0) {
                AlertDialogPrepromptForAndroidSettings.show(any<Activity>(), any(), any(), any())
            }

            activityHandlers.last().onActivityAvailable(hostActivity)

            verify(exactly = 1) {
                AlertDialogPrepromptForAndroidSettings.show(hostActivity, any(), any(), any())
            }
            verify(exactly = 1) { mockAppService.removeActivityLifecycleHandler(activityHandlers.last()) }
            locationPermissionChanged shouldBe null

            callbackSlot.captured.onDecline()

            locationPermissionChanged shouldBe false
        } finally {
            unmockkObject(AlertDialogPrepromptForAndroidSettings)
        }
    }

    test("onReject with fallback waits for host activity when no activity is foreground") {
        mockkObject(AlertDialogPrepromptForAndroidSettings)

        try {
            val mockRequestPermissionService = mockk<IRequestPermissionService>()
            val activityHandlers = mutableListOf<IActivityLifecycleHandler>()
            val mockAppService = mockk<IApplicationService>()
            val hostActivity = mockk<Activity>(relaxed = true)
            val callbackSlot = slot<AlertDialogPrepromptForAndroidSettings.Callback>()

            every { hostActivity.getString(any()) } returns "Location"
            every { mockAppService.current } returns null
            every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()
            every { mockAppService.addActivityLifecycleHandler(any()) } answers {
                activityHandlers.add(firstArg<IActivityLifecycleHandler>())
            }
            every { mockAppService.removeActivityLifecycleHandler(any()) } just runs
            every { mockAppService.addApplicationLifecycleHandler(any()) } just runs
            every { mockAppService.removeApplicationLifecycleHandler(any()) } just runs
            every {
                AlertDialogPrepromptForAndroidSettings.show(
                    hostActivity,
                    any(),
                    any(),
                    capture(callbackSlot),
                )
            } just runs

            var locationPermissionChanged: Boolean? = null
            val locationPermissionController =
                LocationPermissionController(
                    mockRequestPermissionService,
                    mockAppService,
                )
            locationPermissionController.subscribe(
                object : ILocationPermissionChangedHandler {
                    override fun onLocationPermissionChanged(enabled: Boolean) {
                        locationPermissionChanged = enabled
                    }
                },
            )

            locationPermissionController.onReject(true)

            verify(exactly = 1) { mockAppService.addActivityLifecycleHandler(any()) }
            verify(exactly = 0) {
                AlertDialogPrepromptForAndroidSettings.show(any<Activity>(), any(), any(), any())
            }
            locationPermissionChanged shouldBe null

            activityHandlers.last().onActivityAvailable(hostActivity)

            verify(exactly = 1) {
                AlertDialogPrepromptForAndroidSettings.show(hostActivity, any(), any(), any())
            }
            verify(exactly = 1) { mockAppService.removeActivityLifecycleHandler(activityHandlers.last()) }

            callbackSlot.captured.onDecline()

            locationPermissionChanged shouldBe false
        } finally {
            unmockkObject(AlertDialogPrepromptForAndroidSettings)
        }
    }

    test("onReject with fallback shows settings dialog immediately when a host activity is already foreground") {
        mockkObject(AlertDialogPrepromptForAndroidSettings)

        try {
            val mockRequestPermissionService = mockk<IRequestPermissionService>()
            val mockAppService = mockk<IApplicationService>()
            val hostActivity = mockk<Activity>(relaxed = true)
            val callbackSlot = slot<AlertDialogPrepromptForAndroidSettings.Callback>()

            every { hostActivity.getString(any()) } returns "Location"
            every { mockAppService.current } returns hostActivity
            every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()
            every {
                AlertDialogPrepromptForAndroidSettings.show(
                    hostActivity,
                    any(),
                    any(),
                    capture(callbackSlot),
                )
            } just runs

            var locationPermissionChanged: Boolean? = null
            val locationPermissionController =
                LocationPermissionController(
                    mockRequestPermissionService,
                    mockAppService,
                )
            locationPermissionController.subscribe(
                object : ILocationPermissionChangedHandler {
                    override fun onLocationPermissionChanged(enabled: Boolean) {
                        locationPermissionChanged = enabled
                    }
                },
            )

            locationPermissionController.onReject(true)

            // No deferral: the dialog is shown right away on the foreground host activity.
            verify(exactly = 0) { mockAppService.addActivityLifecycleHandler(any()) }
            verify(exactly = 1) {
                AlertDialogPrepromptForAndroidSettings.show(hostActivity, any(), any(), any())
            }
            locationPermissionChanged shouldBe null

            callbackSlot.captured.onDecline()

            locationPermissionChanged shouldBe false
        } finally {
            unmockkObject(AlertDialogPrepromptForAndroidSettings)
        }
    }
})
