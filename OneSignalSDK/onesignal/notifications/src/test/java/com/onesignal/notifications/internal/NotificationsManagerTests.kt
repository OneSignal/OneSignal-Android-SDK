package com.onesignal.notifications.internal

import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.threading.runOnSerialIO
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.common.threading.withMain
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.data.INotificationRepository
import com.onesignal.notifications.internal.lifecycle.INotificationLifecycleService
import com.onesignal.notifications.internal.permissions.INotificationPermissionController
import com.onesignal.notifications.internal.restoration.INotificationRestoreWorkManager
import com.onesignal.notifications.internal.summary.INotificationSummaryManager
import com.onesignal.notifications.shadows.ShadowRoboNotificationManager
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
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

    fun newManager(
        restoreWorkManager: INotificationRestoreWorkManager =
            mockk {
                every { beginEnqueueingWork(any(), any()) } just runs
            },
        permissionController: INotificationPermissionController =
            mockk {
                every { subscribe(any()) } just runs
            },
    ): NotificationsManager {
        val mockAppService = mockk<IApplicationService>()
        every { mockAppService.addApplicationLifecycleHandler(any()) } just runs
        every { mockAppService.appContext } returns ApplicationProvider.getApplicationContext()

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

    test("the dispatched refresh enqueues the restore work") {
        val dispatched = slot<() -> Unit>()
        every { runOnSerialIO(capture(dispatched)) } just runs
        val restoreWorkManager = mockk<INotificationRestoreWorkManager>(relaxed = true)
        val manager = newManager(restoreWorkManager)
        manager.onFocus(firedOnSubscribe = false)

        dispatched.captured()

        verify(exactly = 1) { restoreWorkManager.beginEnqueueingWork(any(), false) }
    }

    test("an exception from the restore enqueue stays inside the refresh") {
        val dispatched = slot<() -> Unit>()
        every { runOnSerialIO(capture(dispatched)) } just runs
        val manager =
            newManager(
                restoreWorkManager =
                mockk {
                    // WorkManager's own initialization is what throws in the field.
                    every { beginEnqueueingWork(any(), any()) } throws IllegalStateException("WorkManager is not initialized")
                },
            )
        manager.onFocus(firedOnSubscribe = false)

        // The refresh runs on the serial IO dispatcher, where an escape would take the process down.
        shouldNotThrowAny { dispatched.captured() }
    }

    test("a LinkageError from the restore enqueue stays inside the refresh") {
        val dispatched = slot<() -> Unit>()
        every { runOnSerialIO(capture(dispatched)) } just runs
        val manager =
            newManager(
                restoreWorkManager =
                mockk {
                    every { beginEnqueueingWork(any(), any()) } throws NoSuchMethodError("forNamespace")
                },
            )
        manager.onFocus(firedOnSubscribe = false)

        shouldNotThrowAny { dispatched.captured() }
    }

    test("requestPermission reports not granted when the main thread is unavailable") {
        val permissionController = mockk<INotificationPermissionController>(relaxed = true)
        val manager = newManager(permissionController = permissionController)
        coEvery { withMain(any<suspend CoroutineScope.() -> Boolean>()) } returns null

        val granted = runBlocking { manager.requestPermission(true) }

        granted shouldBe false
        coVerify(exactly = 0) { permissionController.prompt(any()) }
    }

    test("requestPermission returns what the prompt answered") {
        val manager = newManager()
        coEvery { withMain(any<suspend CoroutineScope.() -> Boolean>()) } returns true

        runBlocking { manager.requestPermission(true) } shouldBe true
    }
})
