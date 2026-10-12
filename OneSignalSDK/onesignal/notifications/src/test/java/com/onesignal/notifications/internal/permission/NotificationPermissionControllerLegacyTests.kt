package com.onesignal.notifications.internal.permission

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.permissions.AlertDialogPrepromptForAndroidSettings
import com.onesignal.core.internal.permissions.IRequestPermissionService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.mocks.MockHelper
import com.onesignal.notifications.internal.permissions.INotificationPermissionChangedHandler
import com.onesignal.notifications.internal.permissions.impl.NotificationPermissionController
import com.onesignal.notifications.shadows.ShadowRoboNotificationManager
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap

private const val FALLBACK_ALLOWED_KEY = PreferenceOneSignalKeys.PREFS_OS_NOTIFICATION_SETTINGS_FALLBACK_ALLOWED

private suspend fun awaitCondition(condition: () -> Boolean) =
    withTimeout(2_000) {
        while (!condition()) delay(10)
    }

@Config(
    packageName = "com.onesignal.example",
    shadows = [ShadowRoboNotificationManager::class],
    sdk = [31],
)
@RobolectricTest
class NotificationPermissionControllerLegacyTests : FunSpec({
    lateinit var prefs: ConcurrentHashMap<String, Boolean>
    lateinit var mockPreferenceService: IPreferencesService
    lateinit var mockAppService: IApplicationService
    lateinit var lifecycleHandlers: MutableList<IApplicationLifecycleHandler>
    lateinit var hostActivity: Activity
    lateinit var dialogCallback: CapturingSlot<AlertDialogPrepromptForAndroidSettings.Callback>

    fun installApp(
        firstInstallTime: Long,
        lastUpdateTime: Long,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context.packageManager).installPackage(
            PackageInfo().also {
                it.packageName = context.packageName
                it.firstInstallTime = firstInstallTime
                it.lastUpdateTime = lastUpdateTime
                it.applicationInfo = ApplicationInfo().also { app -> app.packageName = context.packageName }
            },
        )
    }

    suspend fun startSession(focused: Boolean = true): NotificationPermissionController {
        val controller =
            NotificationPermissionController(
                mockAppService,
                mockk<IRequestPermissionService>().also {
                    every { it.registerAsCallback(any(), any()) } just runs
                },
                mockAppService,
                mockPreferenceService,
                MockHelper.configModelStore(),
            )
        if (focused) lifecycleHandlers.forEach { it.onFocus(false) }
        awaitCondition { prefs[FALLBACK_ALLOWED_KEY] == true }
        return controller
    }

    suspend fun promptAndDecline(controller: NotificationPermissionController): Boolean =
        coroutineScope {
            val pending = async { controller.prompt(true) }
            awaitCondition { dialogCallback.isCaptured }
            dialogCallback.captured.onDecline()
            pending.await()
        }

    beforeEach {
        Logging.logLevel = LogLevel.NONE
        ShadowRoboNotificationManager.reset()
        installApp(firstInstallTime = 1_000, lastUpdateTime = 1_000)

        prefs = ConcurrentHashMap()
        mockPreferenceService = mockk(relaxed = true)
        every { mockPreferenceService.getBool(any(), any(), any()) } answers {
            prefs[secondArg()] ?: thirdArg<Boolean?>()
        }
        every { mockPreferenceService.saveBool(any(), any(), any()) } answers {
            prefs[secondArg()] = thirdArg<Boolean?>() ?: false
        }

        hostActivity = mockk(relaxed = true)
        every { hostActivity.getString(any()) } returns "Notifications"
        lifecycleHandlers = mutableListOf()
        mockAppService = mockk(relaxed = true)
        every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()
        every { mockAppService.current } returns hostActivity
        every { mockAppService.addApplicationLifecycleHandler(any()) } answers {
            lifecycleHandlers.add(firstArg())
        }

        dialogCallback = slot()
        mockkObject(AlertDialogPrepromptForAndroidSettings)
        every {
            AlertDialogPrepromptForAndroidSettings.show(any(), any(), any(), capture(dialogCallback))
        } just runs
    }

    afterEach {
        unmockkObject(AlertDialogPrepromptForAndroidSettings)
    }

    test("first session with notifications never enabled defers every settings fallback request") {
        ShadowRoboNotificationManager.notificationsEnabled = false
        val controller = startSession()

        withTimeout(1_000) { controller.prompt(true) } shouldBe false
        withTimeout(1_000) { controller.prompt(true) } shouldBe false

        verify(exactly = 0) { AlertDialogPrepromptForAndroidSettings.show(any(), any(), any(), any()) }
    }

    test("simultaneous first session requests do not show the settings dialog") {
        ShadowRoboNotificationManager.notificationsEnabled = false
        val controller = startSession()

        val results =
            withTimeout(1_000) {
                coroutineScope {
                    listOf(async { controller.prompt(true) }, async { controller.prompt(true) }).awaitAll()
                }
            }

        results shouldBe listOf(false, false)
        verify(exactly = 0) { AlertDialogPrepromptForAndroidSettings.show(any(), any(), any(), any()) }
    }

    test("session is decided at startup, so a later session shows the dialog on its first request") {
        ShadowRoboNotificationManager.notificationsEnabled = false
        startSession()

        promptAndDecline(startSession()) shouldBe false

        verify(exactly = 1) { AlertDialogPrepromptForAndroidSettings.show(hostActivity, any(), any(), any()) }
    }

    test("an updated app shows the dialog on its first request, since it launched before the update") {
        installApp(firstInstallTime = 1_000, lastUpdateTime = 2_000)
        ShadowRoboNotificationManager.notificationsEnabled = false

        promptAndDecline(startSession()) shouldBe false

        verify(exactly = 1) { AlertDialogPrepromptForAndroidSettings.show(hostActivity, any(), any(), any()) }
    }

    test("a prompt that finds notifications enabled lifts the deferral before polling sees it") {
        ShadowRoboNotificationManager.notificationsEnabled = false
        val controller = startSession(focused = false)

        ShadowRoboNotificationManager.notificationsEnabled = true
        withTimeout(1_000) { controller.prompt(true) } shouldBe true
        ShadowRoboNotificationManager.notificationsEnabled = false

        promptAndDecline(controller) shouldBe false
        verify(exactly = 1) { AlertDialogPrepromptForAndroidSettings.show(hostActivity, any(), any(), any()) }
    }

    test("notifications seen enabled this session show the dialog once they are turned off") {
        val controller = startSession()

        ShadowRoboNotificationManager.notificationsEnabled = false
        promptAndDecline(controller) shouldBe false

        verify(exactly = 1) { AlertDialogPrepromptForAndroidSettings.show(hostActivity, any(), any(), any()) }
    }

    test("an Allow on the OEM prompt after a deferral fires the permission changed event") {
        ShadowRoboNotificationManager.notificationsEnabled = false
        val controller = startSession()
        var changedTo: Boolean? = null
        controller.subscribe(
            object : INotificationPermissionChangedHandler {
                override fun onNotificationPermissionChanged(enabled: Boolean) {
                    changedTo = enabled
                }
            },
        )
        withTimeout(1_000) { controller.prompt(true) } shouldBe false

        ShadowRoboNotificationManager.notificationsEnabled = true
        lifecycleHandlers.forEach { it.onFocus(false) }

        awaitCondition { changedTo == true }
    }
})
