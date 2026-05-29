package com.onesignal.core.internal.application

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.colman.kotest.android.extensions.robolectric.RobolectricTest
import com.onesignal.common.threading.WaiterWithValue
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.application.impl.ApplicationService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.delay
import org.robolectric.Robolectric

@RobolectricTest
class ApplicationServiceTests : FunSpec({

    beforeAny {
        Logging.logLevel = LogLevel.NONE
    }

    test("start application service with non-activity shows entry state as closed") {
        // Given
        val context = ApplicationProvider.getApplicationContext<Context>()
        val applicationService = ApplicationService()

        // When
        applicationService.start(context)
        val entryState = applicationService.entryState

        // Then
        entryState shouldBe AppEntryAction.APP_CLOSE
    }

    test("start application service with activity shows entry state as closed") {
        // Given
        val controller1 = Robolectric.buildActivity(Activity::class.java)
        controller1.setup() // Moves Activity to RESUMED state
        val activity = controller1.get()
        val applicationService = ApplicationService()

        // When
        applicationService.start(activity)
        val entryState = applicationService.entryState

        // Then
        entryState shouldBe AppEntryAction.APP_OPEN
    }

    test("current activity is established when activity is started") {
        // Given
        val controller1 = Robolectric.buildActivity(Activity::class.java)
        controller1.setup() // Moves Activity to RESUMED state
        val activity1 = controller1.get()
        val controller2 = Robolectric.buildActivity(Activity::class.java)
        controller2.setup() // Moves Activity to RESUMED state
        val activity2 = controller2.get()

        val applicationService = ApplicationService()

        // When
        applicationService.start(activity1)

        val initialActivity = applicationService.current
        pushActivity(applicationService, activity1, activity2)
        val currentActivity = applicationService.current

        // Then
        initialActivity shouldBe activity1
        currentActivity shouldBe activity2
    }

    test("current activity is established when activity is stopped") {
        // Given
        val controller1 = Robolectric.buildActivity(Activity::class.java)
        controller1.setup() // Moves Activity to RESUMED state
        val activity1 = controller1.get()
        val controller2 = Robolectric.buildActivity(Activity::class.java)
        controller2.setup() // Moves Activity to RESUMED state
        val activity2 = controller2.get()

        val applicationService = ApplicationService()

        // When
        applicationService.start(activity1)
        val initialActivity = applicationService.current
        pushActivity(applicationService, activity1, activity2)
        popActivity(applicationService, activity2, activity1)
        val currentActivity = applicationService.current

        // Then
        initialActivity shouldBe activity1
        currentActivity shouldBe activity1
    }

    test("unfocus will occur when when all activities are stopped") {
        // Given
        val controller1 = Robolectric.buildActivity(Activity::class.java)
        controller1.setup() // Moves Activity to RESUMED state
        val activity1 = controller1.get()
        val controller2 = Robolectric.buildActivity(Activity::class.java)
        controller2.setup() // Moves Activity to RESUMED state
        val activity2 = controller2.get()

        val mockApplicationLifecycleHandler = spyk<IApplicationLifecycleHandler>()
        val applicationService = ApplicationService()
        applicationService.addApplicationLifecycleHandler(mockApplicationLifecycleHandler)

        // When
        applicationService.start(activity1)

        pushActivity(applicationService, activity1, activity2)
        applicationService.onActivityPaused(activity2)
        applicationService.onActivityStopped(activity2)

        val currentActivity = applicationService.current

        // Then
        currentActivity shouldBe null
        verify(exactly = 1) { mockApplicationLifecycleHandler.onUnfocused() }
    }

    test("focus will occur when when the first activity is started") {
        // Given
        val controller1 = Robolectric.buildActivity(Activity::class.java)
        controller1.setup() // Moves Activity to RESUMED state
        val activity1 = controller1.get()
        val controller2 = Robolectric.buildActivity(Activity::class.java)
        controller2.setup() // Moves Activity to RESUMED state
        val activity2 = controller2.get()

        val mockApplicationLifecycleHandler = spyk<IApplicationLifecycleHandler>()
        val applicationService = ApplicationService()
        applicationService.addApplicationLifecycleHandler(mockApplicationLifecycleHandler)

        // When
        applicationService.start(activity1)

        pushActivity(applicationService, activity1, activity2)
        applicationService.onActivityPaused(activity2)
        applicationService.onActivityStopped(activity2)

        applicationService.onActivityStarted(activity2)
        applicationService.onActivityResumed(activity2)

        val currentActivity = applicationService.current

        // Then
        currentActivity shouldBe activity2
        verify(exactly = 1) { mockApplicationLifecycleHandler.onUnfocused() }
        verify(exactly = 1) { mockApplicationLifecycleHandler.onFocus(false) }
    }

    test("focus will occur on subscribe when activity is already started") {
        // Given
        val controller1 = Robolectric.buildActivity(Activity::class.java)
        controller1.setup() // Moves Activity to RESUMED state
        val activity = controller1.get()

        val applicationService = ApplicationService()
        val mockApplicationLifecycleHandler = spyk<IApplicationLifecycleHandler>()

        // When
        applicationService.start(activity)
        applicationService.addApplicationLifecycleHandler(mockApplicationLifecycleHandler)

        // Then
        verify(exactly = 1) { mockApplicationLifecycleHandler.onFocus(true) }
    }

    test("wait until system condition returns false when there is no activity") {
        // Given
        val applicationService = ApplicationService()

        // When
        val response = applicationService.waitUntilSystemConditionsAvailable()

        // Then
        response shouldBe false
    }

    test("wait until system condition returns false if activity not started within 5 seconds") {
        // Given
        val controller1 = Robolectric.buildActivity(Activity::class.java)
        controller1.setup() // Moves Activity to RESUMED state
        val activity = controller1.get()

        val applicationService = ApplicationService()

        val waiter = WaiterWithValue<Boolean>()

        // When
        suspendifyOnIO {
            val response = applicationService.waitUntilSystemConditionsAvailable()
            waiter.wake(response)
        }

        delay(7000)

        applicationService.onActivityStarted(activity)
        val response = waiter.waitForWake()

        // Then
        response shouldBe false
    }

    test("wait until system condition returns true when an activity is started within 5 seconds") {
        // Given
        val controller1 = Robolectric.buildActivity(Activity::class.java)
        controller1.setup() // Moves Activity to RESUMED state
        val activity = controller1.get()

        val applicationService = ApplicationService()

        val waiter = WaiterWithValue<Boolean>()

        // When
        suspendifyOnIO {
            val response = applicationService.waitUntilSystemConditionsAvailable()
            waiter.wake(response)
        }

        delay(3000)

        applicationService.onActivityStarted(activity)
        val response = waiter.waitForWake()

        // Then
        response shouldBe true
    }

    test("wait until system condition returns true when there is no system condition") {
        // Given
        val controller1 = Robolectric.buildActivity(Activity::class.java)
        controller1.setup() // Moves Activity to RESUMED state
        val activity = controller1.get()
        val applicationService = ApplicationService()

        // When
        applicationService.start(activity)
        val response = applicationService.waitUntilSystemConditionsAvailable()

        // Then
        response shouldBe true
    }

    test("internal trampoline activity does not affect focus, current, or entry state") {
        // Given
        val mainController = Robolectric.buildActivity(Activity::class.java)
        mainController.setup()
        val mainActivity = mainController.get()

        val trampolineController = Robolectric.buildActivity(InternalTrampolineActivity::class.java)
        trampolineController.setup()
        val trampoline = trampolineController.get()

        val handler = spyk<IApplicationLifecycleHandler>()
        val applicationService = ApplicationService()
        applicationService.addApplicationLifecycleHandler(handler)

        // Simulate late cold-start init from a non-activity context, with the notification
        // module having classified entry as NOTIFICATION_CLICK.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        applicationService.start(appContext)
        applicationService.entryState = AppEntryAction.NOTIFICATION_CLICK

        // When: the trampoline runs its full lifecycle.
        applicationService.onActivityStarted(trampoline)
        applicationService.onActivityResumed(trampoline)
        applicationService.onActivityStopped(trampoline)

        // Then: it is fully ignored — entry state is preserved and no focus is taken.
        applicationService.entryState shouldBe AppEntryAction.NOTIFICATION_CLICK
        applicationService.current shouldBe null

        // When: the real activity starts after the trampoline finishes.
        applicationService.onActivityStarted(mainActivity)

        // Then: focus is established but the NOTIFICATION_CLICK entry state is retained.
        applicationService.current shouldBe mainActivity
        applicationService.entryState shouldBe AppEntryAction.NOTIFICATION_CLICK
        verify(exactly = 0) { handler.onUnfocused() }
    }

    test("stopping an activity whose start was never counted does not drop focus") {
        // Given
        val mainController = Robolectric.buildActivity(Activity::class.java)
        mainController.setup()
        val mainActivity = mainController.get()

        val unseenController = Robolectric.buildActivity(Activity::class.java)
        unseenController.setup()
        val unseen = unseenController.get()

        val handler = spyk<IApplicationLifecycleHandler>()
        val applicationService = ApplicationService()
        applicationService.addApplicationLifecycleHandler(handler)

        // current is established for mainActivity, reference count is 1.
        applicationService.start(mainActivity)

        // When: an activity the SDK never observed starting now stops (late-init trampoline).
        applicationService.onActivityStopped(unseen)

        // Then: the reference count does not underflow and focus is retained.
        applicationService.current shouldBe mainActivity
        verify(exactly = 0) { handler.onUnfocused() }
    }

    test("cold start from notification fires focus when lifecycle observer is installed at process start") {
        // Given: the production path where androidx.startup registered the observer at process start.
        val application = ApplicationProvider.getApplicationContext<Application>()

        val mainController = Robolectric.buildActivity(Activity::class.java)
        mainController.setup()
        val mainActivity = mainController.get()

        val handler = spyk<IApplicationLifecycleHandler>()
        val applicationService = ApplicationService()

        // The observer is attached before init runs (no activity observed yet — only the internal
        // trampoline, which is ignored).
        applicationService.attachToApplication(application)

        // Late init from a non-activity (trampoline) context.
        applicationService.start(application)
        applicationService.addApplicationLifecycleHandler(handler)

        // The notification module classifies the entry before the host activity launches.
        applicationService.entryState = AppEntryAction.NOTIFICATION_CLICK

        // When: the host activity launches.
        applicationService.onActivityStarted(mainActivity)
        applicationService.onActivityResumed(mainActivity)

        // Then: focus is delivered exactly once and the notification entry state is preserved.
        verify(exactly = 1) { handler.onFocus(false) }
        applicationService.current shouldBe mainActivity
        applicationService.entryState shouldBe AppEntryAction.NOTIFICATION_CLICK
    }

    test("configuration change recreation does not inflate the reference count") {
        // Given
        val firstController = Robolectric.buildActivity(Activity::class.java)
        firstController.setup()
        val firstInstance = firstController.get()

        val recreatedController = Robolectric.buildActivity(Activity::class.java)
        recreatedController.setup()
        val recreatedInstance = recreatedController.get()

        val handler = spyk<IApplicationLifecycleHandler>()
        val applicationService = ApplicationService()
        applicationService.addApplicationLifecycleHandler(handler)

        // current is established for the first instance, reference count is 1.
        applicationService.start(firstInstance)

        // When: a config change (e.g. rotation) destroys the old instance and starts a new one.
        // The old instance's stop reports isChangingConfigurations = true.
        val rotatingOldInstance = spyk(firstInstance)
        every { rotatingOldInstance.isChangingConfigurations } returns true
        applicationService.onActivityPaused(rotatingOldInstance)
        applicationService.onActivityStopped(rotatingOldInstance)
        applicationService.onActivityStarted(recreatedInstance)
        applicationService.onActivityResumed(recreatedInstance)

        // Then: focus is retained throughout, current points at the recreated instance.
        applicationService.current shouldBe recreatedInstance
        verify(exactly = 0) { handler.onUnfocused() }

        // When: the app is now genuinely backgrounded (single, non-config stop).
        applicationService.onActivityPaused(recreatedInstance)
        applicationService.onActivityStopped(recreatedInstance)

        // Then: focus is lost exactly once — the count never climbed during rotation.
        applicationService.current shouldBe null
        verify(exactly = 1) { handler.onUnfocused() }
    }
}) {
    companion object {
        fun pushActivity(
            applicationService: ApplicationService,
            currActivity: Activity,
            newActivity: Activity,
            destoryCurrent: Boolean = false,
        ) {
            applicationService.onActivityPaused(currActivity)
            applicationService.onActivityCreated(newActivity, null)
            applicationService.onActivityStarted(newActivity)
            applicationService.onActivityResumed(newActivity)
            applicationService.onActivityStopped(currActivity)

            if (destoryCurrent) {
                applicationService.onActivityDestroyed(currActivity)
            }
        }

        fun popActivity(
            applicationService: ApplicationService,
            currActivity: Activity,
            oldActivity: Activity,
        ) {
            applicationService.onActivityPaused(currActivity)
            applicationService.onActivityStarted(oldActivity)
            applicationService.onActivityResumed(oldActivity)
            applicationService.onActivityStopped(currActivity)
            applicationService.onActivityDestroyed(currActivity)
        }
    }
}

/** Stands in for an SDK-internal trampoline (e.g. NotificationOpenedActivity) in tests. */
class InternalTrampolineActivity :
    Activity(),
    OneSignalInternalActivity
