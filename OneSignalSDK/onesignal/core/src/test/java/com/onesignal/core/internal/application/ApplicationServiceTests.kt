package com.onesignal.core.internal.application

import android.app.Activity
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
        val activity: Activity

        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity = controller.get()
        }
        val applicationService = ApplicationService()

        // When
        applicationService.start(activity)
        val entryState = applicationService.entryState

        // Then
        entryState shouldBe AppEntryAction.APP_OPEN
    }

    test("current activity is established when activity is started") {
        // Given
        val activity1: Activity
        val activity2: Activity
        val context = ApplicationProvider.getApplicationContext<Context>()
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity1 = controller.get()
        }
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity2 = controller.get()
        }

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
        val activity1: Activity
        val activity2: Activity

        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity1 = controller.get()
        }
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity2 = controller.get()
        }

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
        val activity1: Activity
        val activity2: Activity

        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity1 = controller.get()
        }
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity2 = controller.get()
        }

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
        val activity1: Activity
        val activity2: Activity

        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity1 = controller.get()
        }
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity2 = controller.get()
        }

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
        val activity: Activity

        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity = controller.get()
        }

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
        val activity: Activity
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity = controller.get()
        }
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
        val activity: Activity
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity = controller.get()
        }
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
        val activity: Activity

        Robolectric.buildActivity(Activity::class.java).use { controller ->
            controller.setup() // Moves Activity to RESUMED state
            activity = controller.get()
        }
        val applicationService = ApplicationService()

        // When
        applicationService.start(activity)
        val response = applicationService.waitUntilSystemConditionsAvailable()

        // Then
        response shouldBe true
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
