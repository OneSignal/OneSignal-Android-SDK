package com.onesignal.notifications.internal

import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.threading.runOnSerialIO
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.data.INotificationRepository
import com.onesignal.notifications.internal.lifecycle.INotificationLifecycleService
import com.onesignal.notifications.internal.permissions.INotificationPermissionController
import com.onesignal.notifications.internal.restoration.INotificationRestoreWorkManager
import com.onesignal.notifications.internal.summary.INotificationSummaryManager
import com.onesignal.notifications.shadows.ShadowRoboNotificationManager
import io.kotest.core.spec.style.FunSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.robolectric.annotation.Config

/**
 * Regression coverage for the WorkManager-DB ANR observed in production
 * (OTel sample insertId `9qy5s0ta0cwqwmb0`, vivo I2306 / Android 15: 30.5 s
 * main-thread block at `NotificationRestoreWorkManager.beginEnqueueingWork`
 * fired from `Activity.onStart`).
 *
 * Same Activity-lifecycle fan-out as SDK-4505: `onActivityStarted` -> `handleFocus` ->
 * `applicationLifecycleNotifier.fire { onFocus(...) }` synchronously invokes every
 * `IApplicationLifecycleHandler` on the main thread. `NotificationsManager.onFocus` was
 * doing `WorkManager.enqueueUniqueWork` (which also lazily initializes the WorkManager
 * SQLite store on first call) inline, and the SQLite write stalled the main thread on
 * devices with slow / contended storage.
 *
 * The fix routes through `runOnSerialIO`, which offloads the work to the serial IO
 * dispatcher instead of running it inline on the main thread. These tests assert the
 * dispatch contract on `onFocus`; the helper itself is tested in `:core` against
 * `ThreadUtilsDispatchTests`.
 *
 * `suspendifyOnIO` is also stubbed because `NotificationsManager`'s init block fires it for
 * `deleteExpiredNotifications`; without the stub a real coroutine would leak past test
 * teardown.
 */
@Config(
    packageName = "com.onesignal.example",
    shadows = [ShadowRoboNotificationManager::class],
    sdk = [33],
)
@RobolectricTest
class NotificationsManagerTests : FunSpec({

    val threadUtilsPath = "com.onesignal.common.threading.ThreadUtilsKt"

    beforeEach {
        Logging.logLevel = LogLevel.NONE
        ShadowRoboNotificationManager.reset()
        mockkStatic(threadUtilsPath)
        every { runOnSerialIO(any<() -> Unit>()) } just runs
        every { suspendifyOnIO(any<suspend () -> Unit>()) } just runs
    }

    afterEach {
        unmockkStatic(threadUtilsPath)
    }

    fun newManager(): NotificationsManager {
        val mockAppService = mockk<IApplicationService>()
        every { mockAppService.addApplicationLifecycleHandler(any()) } just runs
        every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()

        val permissionController = mockk<INotificationPermissionController>()
        every { permissionController.subscribe(any()) } just runs

        val restoreWorkManager = mockk<INotificationRestoreWorkManager>()
        every { restoreWorkManager.beginEnqueueingWork(any(), any()) } just runs

        val lifecycleService = mockk<INotificationLifecycleService>(relaxed = true)
        val dataController = mockk<INotificationRepository>(relaxed = true)
        val summaryManager = mockk<INotificationSummaryManager>(relaxed = true)

        return NotificationsManager(
            mockAppService,
            permissionController,
            restoreWorkManager,
            lifecycleService,
            dataController,
            summaryManager,
        )
    }

    test("onFocus dispatches refreshNotificationState through runOnSerialIO") {
        val manager = newManager()

        manager.onFocus(firedOnSubscribe = false)

        verify(exactly = 1) { runOnSerialIO(any<() -> Unit>()) }
    }

    test("rapid onFocus burst dispatches each event through the serial IO helper in submission order") {
        val manager = newManager()

        // Two focus events in quick succession on the main thread (e.g. activity restart
        // bouncing between activities). Both must route through the same serial IO helper in
        // submission order — same defense the BackgroundManager burst test enforces for
        // its `schedule`/`cancel` pair, ensuring future per-event work added here observes
        // events in main-thread arrival order.
        manager.onFocus(firedOnSubscribe = false)
        manager.onFocus(firedOnSubscribe = false)

        verify(exactly = 2) { runOnSerialIO(any<() -> Unit>()) }
        verifyOrder {
            runOnSerialIO(any<() -> Unit>())
            runOnSerialIO(any<() -> Unit>())
        }
    }
})
