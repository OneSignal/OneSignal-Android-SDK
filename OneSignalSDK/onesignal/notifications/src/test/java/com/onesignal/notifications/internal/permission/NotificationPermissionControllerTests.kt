package com.onesignal.notifications.internal.permission

import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
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
import io.mockk.runs
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
