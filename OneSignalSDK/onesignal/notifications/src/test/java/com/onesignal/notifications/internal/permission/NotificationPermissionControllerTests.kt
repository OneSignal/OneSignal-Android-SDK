package com.onesignal.notifications.internal.permission

import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.AndroidMockHelper
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

    test("NotificationPermissionController permission polling fires permission changed event") {
        // Given
        val mockRequestPermissionService = mockk<IRequestPermissionService>()
        every { mockRequestPermissionService.registerAsCallback(any(), any()) } just runs
        val mockPreferenceService = mockk<IPreferencesService>()

        var handlerFired = false
        val notificationPermissionController = NotificationPermissionController(AndroidMockHelper.applicationService(), mockRequestPermissionService, AndroidMockHelper.applicationService(), mockPreferenceService, MockHelper.configModelStore())

        notificationPermissionController.subscribe(
            object : INotificationPermissionChangedHandler {
                override fun onNotificationPermissionChanged(enabled: Boolean) {
                    handlerFired = true
                }
            },
        )

        // When
        // permission changes
        ShadowRoboNotificationManager.setNotificationsEnabled(false)
        delay(5)

        // Then
        // permissionChanged Event should fire
        handlerFired shouldBe true
    }
})
