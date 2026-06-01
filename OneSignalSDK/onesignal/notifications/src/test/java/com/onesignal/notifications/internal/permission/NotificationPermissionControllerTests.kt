package com.onesignal.notifications.internal.permission

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.runOnSerialIOIfBackgroundThreading
import com.onesignal.core.activities.PermissionsActivity
import com.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.permissions.AlertDialogPrepromptForAndroidSettings
import com.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.internal.permissions.INotificationPermissionChangedHandler
import com.onesignal.notifications.internal.permissions.impl.NotificationPermissionController
import com.onesignal.notifications.shadows.ShadowRoboNotificationManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.robolectric.annotation.Config

@Config(
    packageName = "com.onesignal.example",
    shadows = [ShadowRoboNotificationManager::class],
    sdk = [33],
)
@RobolectricTest
class NotificationPermissionControllerTests : FunSpec({
    beforeAny {
        Logging.logLevel = LogLevel.NONE
        ShadowRoboNotificationManager.reset()
    }

    beforeEach {
        ShadowRoboNotificationManager.reset()
    }

    test("NotificationPermissionController permission polling fires permission changed event") {
        // Given
        val mockRequestPermissionService = mockk<IRequestPermissionService>()
        every { mockRequestPermissionService.registerAsCallback(any(), any()) } just runs
        val mockPreferenceService = mockk<IPreferencesService>()
        val focusHandlerList = mutableListOf<IApplicationLifecycleHandler>()
        val mockAppService = mockk<IApplicationService>()
        every { mockAppService.addApplicationLifecycleHandler(any()) } answers {
            focusHandlerList.add(firstArg<IApplicationLifecycleHandler>())
        }
        every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()
        var handlerFired = false
        val notificationPermissionController = NotificationPermissionController(mockAppService, mockRequestPermissionService, mockAppService, mockPreferenceService, MockHelper.configModelStore())

        notificationPermissionController.subscribe(
            object : INotificationPermissionChangedHandler {
                override fun onNotificationPermissionChanged(enabled: Boolean) {
                    handlerFired = true
                }
            },
        )
        // call onFocus to set the proper polling interval.
        // This happens when registering the lifecycle handler
        for (focusHandler in focusHandlerList) {
            focusHandler.onFocus(false)
        }

        // When
        // permission changes
        ShadowRoboNotificationManager.setShadowNotificationsEnabled(false)
        delay(100)

        // Then
        // permissionChanged Event should fire
        handlerFired shouldBe true
    }

    test("NotificationPermissionController permission polling pauses when app loses") {
        // Given
        val mockRequestPermissionService = mockk<IRequestPermissionService>()
        every { mockRequestPermissionService.registerAsCallback(any(), any()) } just runs
        val mockPreferenceService = mockk<IPreferencesService>()
        val handlerList = mutableListOf<IApplicationLifecycleHandler>()
        val mockAppService = mockk<IApplicationService>()
        every { mockAppService.addApplicationLifecycleHandler(any()) } answers {
            handlerList.add(firstArg<IApplicationLifecycleHandler>())
        }
        every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()

        var handlerFired = false
        val notificationPermissionController = NotificationPermissionController(mockAppService, mockRequestPermissionService, mockAppService, mockPreferenceService, MockHelper.configModelStore())

        notificationPermissionController.subscribe(
            object : INotificationPermissionChangedHandler {
                override fun onNotificationPermissionChanged(enabled: Boolean) {
                    handlerFired = true
                }
            },
        )
        // call onFocus to set the proper polling interval.
        // This happens when registering the lifecycle handler
        for (focusHandler in handlerList) {
            focusHandler.onFocus(false)
        }

        // When
        // the app has loses focus
        for (handler in handlerList) {
            handler.onUnfocused()
        }
        delay(100)
        // the permission changes
        ShadowRoboNotificationManager.setShadowNotificationsEnabled(false)
        delay(100)

        // Then
        // permissionChanged Event should not fire
        handlerFired shouldBe false
    }

    test("onReject with fallback waits for host activity before showing settings dialog") {
        mockkObject(AlertDialogPrepromptForAndroidSettings)

        try {
            val mockRequestPermissionService = mockk<IRequestPermissionService>()
            every { mockRequestPermissionService.registerAsCallback(any(), any()) } just runs
            val mockPreferenceService = mockk<IPreferencesService>()
            val activityHandlers = mutableListOf<IActivityLifecycleHandler>()
            val mockAppService = mockk<IApplicationService>()
            val permissionsActivity = mockk<PermissionsActivity>(relaxed = true)
            val hostActivity = mockk<Activity>(relaxed = true)
            val callbackSlot = slot<AlertDialogPrepromptForAndroidSettings.Callback>()

            every { hostActivity.getString(any()) } returns "Notifications"
            every { mockAppService.current } returns permissionsActivity
            every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()
            every { mockAppService.addApplicationLifecycleHandler(any()) } just runs
            every { mockAppService.addActivityLifecycleHandler(any()) } answers {
                activityHandlers.add(firstArg<IActivityLifecycleHandler>())
            }
            every { mockAppService.removeActivityLifecycleHandler(any()) } just runs
            every {
                AlertDialogPrepromptForAndroidSettings.show(
                    hostActivity,
                    any(),
                    any(),
                    capture(callbackSlot),
                )
            } just runs

            var notificationPermissionChanged: Boolean? = null
            val notificationPermissionController =
                NotificationPermissionController(
                    mockAppService,
                    mockRequestPermissionService,
                    mockAppService,
                    mockPreferenceService,
                    MockHelper.configModelStore(),
                )
            notificationPermissionController.subscribe(
                object : INotificationPermissionChangedHandler {
                    override fun onNotificationPermissionChanged(enabled: Boolean) {
                        notificationPermissionChanged = enabled
                    }
                },
            )

            notificationPermissionController.onReject(true)

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
            notificationPermissionChanged shouldBe null

            callbackSlot.captured.onDecline()

            notificationPermissionChanged shouldBe false
        } finally {
            unmockkObject(AlertDialogPrepromptForAndroidSettings)
        }
    }

    test("onReject with fallback shows settings dialog immediately when a host activity is already foreground") {
        mockkObject(AlertDialogPrepromptForAndroidSettings)

        try {
            val mockRequestPermissionService = mockk<IRequestPermissionService>()
            every { mockRequestPermissionService.registerAsCallback(any(), any()) } just runs
            val mockPreferenceService = mockk<IPreferencesService>()
            val mockAppService = mockk<IApplicationService>()
            val hostActivity = mockk<Activity>(relaxed = true)
            val callbackSlot = slot<AlertDialogPrepromptForAndroidSettings.Callback>()

            every { hostActivity.getString(any()) } returns "Notifications"
            every { mockAppService.current } returns hostActivity
            every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()
            every { mockAppService.addApplicationLifecycleHandler(any()) } just runs
            every {
                AlertDialogPrepromptForAndroidSettings.show(
                    hostActivity,
                    any(),
                    any(),
                    capture(callbackSlot),
                )
            } just runs

            var notificationPermissionChanged: Boolean? = null
            val notificationPermissionController =
                NotificationPermissionController(
                    mockAppService,
                    mockRequestPermissionService,
                    mockAppService,
                    mockPreferenceService,
                    MockHelper.configModelStore(),
                )
            notificationPermissionController.subscribe(
                object : INotificationPermissionChangedHandler {
                    override fun onNotificationPermissionChanged(enabled: Boolean) {
                        notificationPermissionChanged = enabled
                    }
                },
            )

            notificationPermissionController.onReject(true)

            // No deferral: the dialog is shown right away on the foreground host activity.
            verify(exactly = 0) { mockAppService.addActivityLifecycleHandler(any()) }
            verify(exactly = 1) {
                AlertDialogPrepromptForAndroidSettings.show(hostActivity, any(), any(), any())
            }
            notificationPermissionChanged shouldBe null

            callbackSlot.captured.onDecline()

            notificationPermissionChanged shouldBe false
        } finally {
            unmockkObject(AlertDialogPrepromptForAndroidSettings)
        }
    }

    test("onFocus dispatches polling-interval update + waker through runOnSerialIOIfBackgroundThreading (SDK-4507)") {
        // SDK-4507: the lifecycle-registered onFocus handler reads ConfigModel and calls
        // Waiter.wake(), the latter of which dispatches a coroutine resume into the IO pool.
        // On cold start this is the SDK's first OneSignalDispatchers consumer in the process,
        // and the executor + dispatcher + scope lazy chain pinned the main thread for many
        // seconds under sdk_background_threading. The fix routes through
        // runOnSerialIOIfBackgroundThreading; verify that contract here.
        //
        // We stub the helper so the wrapped block does not run (we don't want a real
        // pollingWaiter.wake() to spawn a real coroutine from this test). The FF branches of
        // the helper itself are covered in :core's ThreadUtilsFeatureFlagTests, which has
        // direct access to the internal ThreadingMode flag.
        val threadUtilsPath = "com.onesignal.common.threading.ThreadUtilsKt"
        mockkStatic(threadUtilsPath)
        mockkObject(OneSignalDispatchers)
        every { runOnSerialIOIfBackgroundThreading(any<() -> Unit>()) } just runs
        every { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        try {
            val mockRequestPermissionService = mockk<IRequestPermissionService>()
            every { mockRequestPermissionService.registerAsCallback(any(), any()) } just runs
            val mockPreferenceService = mockk<IPreferencesService>()
            val focusHandlerList = mutableListOf<IApplicationLifecycleHandler>()
            val mockAppService = mockk<IApplicationService>()
            every { mockAppService.addApplicationLifecycleHandler(any()) } answers {
                focusHandlerList.add(firstArg<IApplicationLifecycleHandler>())
            }
            every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()

            NotificationPermissionController(
                mockAppService,
                mockRequestPermissionService,
                mockAppService,
                mockPreferenceService,
                MockHelper.configModelStore(),
            )

            for (focusHandler in focusHandlerList) {
                focusHandler.onFocus(false)
            }

            // Only the polling lifecycle listener (registered inside the controller's init)
            // routes through the gated helper, so we assert exactly 1 invocation here.
            verify(exactly = 1) { runOnSerialIOIfBackgroundThreading(any<() -> Unit>()) }
        } finally {
            unmockkObject(OneSignalDispatchers)
            unmockkStatic(threadUtilsPath)
        }
    }

    test("onUnfocused dispatches polling-interval reset through runOnSerialIOIfBackgroundThreading (SDK-4507)") {
        val threadUtilsPath = "com.onesignal.common.threading.ThreadUtilsKt"
        mockkStatic(threadUtilsPath)
        mockkObject(OneSignalDispatchers)
        every { runOnSerialIOIfBackgroundThreading(any<() -> Unit>()) } just runs
        every { OneSignalDispatchers.launchOnIO(any<suspend () -> Unit>()) } returns mockk<Job>(relaxed = true)

        try {
            val mockRequestPermissionService = mockk<IRequestPermissionService>()
            every { mockRequestPermissionService.registerAsCallback(any(), any()) } just runs
            val mockPreferenceService = mockk<IPreferencesService>()
            val focusHandlerList = mutableListOf<IApplicationLifecycleHandler>()
            val mockAppService = mockk<IApplicationService>()
            every { mockAppService.addApplicationLifecycleHandler(any()) } answers {
                focusHandlerList.add(firstArg<IApplicationLifecycleHandler>())
            }
            every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()

            NotificationPermissionController(
                mockAppService,
                mockRequestPermissionService,
                mockAppService,
                mockPreferenceService,
                MockHelper.configModelStore(),
            )

            for (focusHandler in focusHandlerList) {
                focusHandler.onUnfocused()
            }

            verify(exactly = 1) { runOnSerialIOIfBackgroundThreading(any<() -> Unit>()) }
        } finally {
            unmockkObject(OneSignalDispatchers)
            unmockkStatic(threadUtilsPath)
        }
    }

    test("NotificationPermissionController permission polling resumes when app gains focus") {
        // Given
        val mockRequestPermissionService = mockk<IRequestPermissionService>()
        every { mockRequestPermissionService.registerAsCallback(any(), any()) } just runs
        val mockPreferenceService = mockk<IPreferencesService>()
        val handlerList = mutableListOf<IApplicationLifecycleHandler>()
        val mockAppService = mockk<IApplicationService>()
        every { mockAppService.addApplicationLifecycleHandler(any()) } answers {
            handlerList.add(firstArg<IApplicationLifecycleHandler>())
        }
        every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()

        var handlerFired = false
        val notificationPermissionController = NotificationPermissionController(mockAppService, mockRequestPermissionService, mockAppService, mockPreferenceService, MockHelper.configModelStore())

        notificationPermissionController.subscribe(
            object : INotificationPermissionChangedHandler {
                override fun onNotificationPermissionChanged(enabled: Boolean) {
                    handlerFired = true
                }
            },
        )
        // call onFocus to set the proper polling interval.
        // This happens when registering the lifecycle handler
        for (focusHandler in handlerList) {
            focusHandler.onFocus(false)
        }

        // When
        // the app loses focus
        for (handler in handlerList) {
            handler.onUnfocused()
        }
        delay(100)
        // the permission changes
        ShadowRoboNotificationManager.setShadowNotificationsEnabled(false)
        delay(100)
        // the app regains focus
        for (handler in handlerList) {
            handler.onFocus(false)
        }
        delay(5)

        // Then
        // permissionChanged Event should fire
        handlerFired shouldBe true
    }
})
