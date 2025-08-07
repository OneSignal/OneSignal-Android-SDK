package com.onesignal.core.internal.application.impl

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.ComponentCallbacks
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.onesignal.common.AndroidUtils
import com.onesignal.common.DeviceUtils
import com.onesignal.common.events.EventProducer
import com.onesignal.common.threading.Waiter
import com.onesignal.core.internal.application.ActivityLifecycleHandlerBase
import com.onesignal.core.internal.application.AppEntryAction
import com.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference

class ApplicationService() : IApplicationService, ActivityLifecycleCallbacks, OnGlobalLayoutListener {
    private val activityLifecycleNotifier = EventProducer<IActivityLifecycleHandler>()
    private val applicationLifecycleNotifier = EventProducer<IApplicationLifecycleHandler>()
    private val systemConditionNotifier = EventProducer<ISystemConditionHandler>()

    override val isInForeground: Boolean
        get() = entryState.isAppOpen || entryState.isNotificationClick
    override var entryState: AppEntryAction = AppEntryAction.APP_CLOSE

    private var _appContext: Context? = null
    override val appContext: Context
        get() = _appContext!!

    private var _current: Activity? = null
    override var current: Activity?
        get() = _current
        set(value) {
            _current = value

            Logging.debug("ApplicationService: current activity=$current")

            if (value != null) {
                activityLifecycleNotifier.fire { it.onActivityAvailable(value) }
                try {
                    value.window.decorView.viewTreeObserver.addOnGlobalLayoutListener(this)
                } catch (e: RuntimeException) {
                    // Related to Unity Issue #239 on Github
                    // https://github.com/OneSignal/OneSignal-Unity-SDK/issues/239
                    // RuntimeException at ActivityLifecycleHandler.setCurActivity on Android (Unity 2.9.0)
                    e.printStackTrace()
                }
            }
        }

    /** Whether the next resume is due to the first activity or not **/
    private var nextResumeIsFirstActivity: Boolean = false

    /** Used to determine when an app goes in focus and out of focus **/
    private var activityReferences = 0
    private var isActivityChangingConfigurations = false

    private val wasInBackground: Boolean
        get() = !isInForeground || nextResumeIsFirstActivity

    /**
     * Retrieve whether the user data is accessible.
     *
     * On Android 7.0+ (API 24+), encrypted user data is inaccessible until the user unlocks the device for the first time after boot.
     * This includes:
     *  * getSharedPreferences()
     *  * Any file-based storage in the default credential-encrypted context
     *
     * Apps that auto-run on boot or background services triggered early may hit this issue.
     */
    override val isDeviceStorageUnlocked: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userManager = appContext.getSystemService(Context.USER_SERVICE) as UserManager
            userManager.isUserUnlocked
        } else {
            // Prior to API 24, the device booted into an unlocked state by default
            true
        }

    /**
     * Call to "start" this service, expected to be called during initialization of the SDK.
     *
     * @param context The context the SDK has been initialized under.
     */
    fun start(context: Context) {
        _appContext = context

        val application = context.applicationContext as Application
        application.registerActivityLifecycleCallbacks(this)

        val configuration =
            object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    // If Activity contains the configChanges orientation flag, re-create the view this way
                    if (current != null &&
                        AndroidUtils.hasConfigChangeFlag(
                            current!!,
                            ActivityInfo.CONFIG_ORIENTATION,
                        )
                    ) {
                        onOrientationChanged(newConfig.orientation, current!!)
                    }
                }

                override fun onLowMemory() {}
            }

        application.registerComponentCallbacks(configuration)

        val isContextActivity = context is Activity
        val isCurrentActivityNull = current == null

        if (!isCurrentActivityNull || isContextActivity) {
            entryState = AppEntryAction.APP_OPEN
            if (isCurrentActivityNull && isContextActivity) {
                current = context as Activity?
                activityReferences = 1
                nextResumeIsFirstActivity = false
            }
        } else {
            nextResumeIsFirstActivity = true
            entryState = AppEntryAction.APP_CLOSE
        }

        Logging.debug("ApplicationService.init: entryState=$entryState")
    }

    override fun addApplicationLifecycleHandler(handler: IApplicationLifecycleHandler) {
        applicationLifecycleNotifier.subscribe(handler)
        if (current != null) {
            // When a listener subscribes, fire its callback
            // The listener is too late to receive the earlier onFocus call
            handler.onFocus(true)
        }
    }

    override fun removeApplicationLifecycleHandler(handler: IApplicationLifecycleHandler) {
        applicationLifecycleNotifier.unsubscribe(handler)
    }

    override fun addActivityLifecycleHandler(handler: IActivityLifecycleHandler) {
        activityLifecycleNotifier.subscribe(handler)
        if (current != null) {
            handler.onActivityAvailable(current!!)
        }
    }

    override fun removeActivityLifecycleHandler(handler: IActivityLifecycleHandler) {
        activityLifecycleNotifier.unsubscribe(handler)
    }

    override fun onActivityCreated(
        activity: Activity,
        bundle: Bundle?,
    ) {
        Logging.debug("ApplicationService.onActivityCreated($activityReferences,$entryState): $activity")
    }

    override fun onActivityStarted(activity: Activity) {
        Logging.debug("ApplicationService.onActivityStarted($activityReferences,$entryState): $activity")

        if (current == activity) {
            return
        }

        current = activity

        if (wasInBackground && !isActivityChangingConfigurations) {
            activityReferences = 1
            handleFocus()
        } else {
            activityReferences++
        }
    }

    override fun onActivityResumed(activity: Activity) {
        Logging.debug("ApplicationService.onActivityResumed($activityReferences,$entryState): $activity")

        // When an activity has something shown above it, it will be paused allowing
        // the new activity to be started (where current is set).  However when that
        // new activity is finished the original activity is simply resumed (it's
        // already been created).  For this case, we make sure current is set
        // to the now current activity.
        if (current != activity) {
            current = activity
        }

        if (wasInBackground && !isActivityChangingConfigurations) {
            activityReferences = 1
            handleFocus()
        }
    }

    override fun onActivityPaused(activity: Activity) {
        Logging.debug("ApplicationService.onActivityPaused($activityReferences,$entryState): $activity")
    }

    override fun onActivityStopped(activity: Activity) {
        Logging.debug("ApplicationService.onActivityStopped($activityReferences,$entryState): $activity")

        isActivityChangingConfigurations = activity.isChangingConfigurations
        if (!isActivityChangingConfigurations && --activityReferences <= 0) {
            current = null
            activityReferences = 0
            handleLostFocus()
        }

        activityLifecycleNotifier.fire { it.onActivityStopped(activity) }
    }

    override fun onActivitySaveInstanceState(
        p0: Activity,
        p1: Bundle,
    ) {
        // Intentionally left empty
    }

    override fun onActivityDestroyed(activity: Activity) {
        Logging.debug("ApplicationService.onActivityDestroyed($activityReferences,$entryState): $activity")
    }

    override fun onGlobalLayout() {
        systemConditionNotifier.fire { it.systemConditionChanged() }
    }

    override suspend fun waitUntilSystemConditionsAvailable(): Boolean {
        var currentActivity = current

        // if this is called just after focusing, it's possible the current activity has not yet
        // been set up.  So we check for up to 5 seconds, then bail if it doesn't happen. We only
        // do this when called on a non-main thread, if we're running on the main thread the
        // activity cannot be setup, so we don't wait around.
        var waitForActivityRetryCount =
            if (AndroidUtils.isRunningOnMainThread()) {
                50
            } else {
                0
            }
        while (currentActivity == null) {
            waitForActivityRetryCount++
            if (waitForActivityRetryCount > 50) {
                Logging.warn("ApplicationService.waitUntilSystemConditionsAvailable: current is null")
                return false
            }
            delay(100)

            currentActivity = current
        }

        try {
            // Detect if user has a dialog fragment showing. If so we suspend until that dialog goes away
            // We cannot detect AlertDialogs because they are added to the decor view as linear layout without an identification
            if (currentActivity is AppCompatActivity) {
                val manager = currentActivity.supportFragmentManager

                val lastFragment = manager.fragments.lastOrNull()
                if (lastFragment != null && lastFragment.isVisible && lastFragment is DialogFragment) {
                    val waiter = Waiter()

                    manager.registerFragmentLifecycleCallbacks(
                        object : FragmentManager.FragmentLifecycleCallbacks() {
                            override fun onFragmentDetached(
                                fm: FragmentManager,
                                fragmentDetached: Fragment,
                            ) {
                                super.onFragmentDetached(fm, fragmentDetached)
                                if (fragmentDetached is DialogFragment) {
                                    manager.unregisterFragmentLifecycleCallbacks(this)
                                    waiter.wake()
                                }
                            }
                        },
                        true,
                    )

                    waiter.waitForWake()
                }
            }
        } catch (exception: NoClassDefFoundError) {
            Logging.info(
                "ApplicationService.waitUntilSystemConditionsAvailable: AppCompatActivity is not used in this app, skipping 'isDialogFragmentShowing' check: $exception",
            )
        }

        val waiter = Waiter()
        val systemConditionHandler =
            object : ISystemConditionHandler {
                override fun systemConditionChanged() {
                    val keyboardUp = DeviceUtils.isKeyboardUp(WeakReference(current))
                    if (!keyboardUp) {
                        waiter.wake()
                    }
                }
            }

        // Add the listener prior to checking the condition to avoid a race condition where
        // we'll never get a callback and will be waiting forever.
        systemConditionNotifier.subscribe(systemConditionHandler)
        val keyboardUp = DeviceUtils.isKeyboardUp(WeakReference(currentActivity))
        // if the keyboard is up we suspend until it is down
        if (keyboardUp) {
            Logging.warn("ApplicationService.waitUntilSystemConditionsAvailable: keyboard up detected")
            waiter.waitForWake()
        }
        systemConditionNotifier.unsubscribe(systemConditionHandler)

        return true
    }

    override suspend fun waitUntilActivityReady(): Boolean {
        val currentActivity = current ?: return false

        val waiter = Waiter()
        decorViewReady(currentActivity) { waiter.wake() }

        waiter.waitForWake()
        return true
    }

    // Ensures the root decor view is ready by checking the following;
    //   1. Is fully attach to the root window and insets are available
    //   2. Ensure if any Activities are changed while waiting we use the updated one
    fun decorViewReady(
        activity: Activity,
        runnable: Runnable,
    ) {
        val listenerKey = "decorViewReady:$runnable"
        val self = this
        activity.window.decorView.post {
            self.addActivityLifecycleHandler(
                object : ActivityLifecycleHandlerBase() {
                    override fun onActivityAvailable(currentActivity: Activity) {
                        self.removeActivityLifecycleHandler(this)
                        if (AndroidUtils.isActivityFullyReady(currentActivity)) {
                            runnable.run()
                        } else {
                            decorViewReady(currentActivity, runnable)
                        }
                    }
                },
            )
        }
    }

    /**
     * Takes pieces from onActivityResumed and onActivityStopped to recreate the view when the
     * phones orientation is changed from manual detection using the onConfigurationChanged callback
     * This fix was originally implemented for In App Messages not being re-shown when orientation
     * was changed on wrapper SDK apps
     */
    private fun onOrientationChanged(
        orientation: Int,
        activity: Activity,
    ) {
        // Log device orientation change
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Logging.debug(
                "ApplicationService.onOrientationChanged: Configuration Orientation Change: LANDSCAPE ($orientation) on activity: $activity",
            )
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            Logging.debug(
                "ApplicationService.onOrientationChanged: Configuration Orientation Change: PORTRAIT ($orientation) on activity: $activity",
            )
        }

        // Remove view
        handleLostFocus()
        activityLifecycleNotifier.fire { it.onActivityStopped(activity) }

        // Show view
        activityLifecycleNotifier.fire { it.onActivityAvailable(activity) }

        activity.window.decorView.viewTreeObserver.addOnGlobalLayoutListener(this)

        handleFocus()
    }

    private fun handleLostFocus() {
        if (isInForeground) {
            Logging.debug("ApplicationService.handleLostFocus: application is now out of focus")

            entryState = AppEntryAction.APP_CLOSE

            applicationLifecycleNotifier.fire { it.onUnfocused() }
        } else {
            Logging.debug("ApplicationService.handleLostFocus: application already out of focus")
        }
    }

    private fun handleFocus() {
        if (wasInBackground) {
            Logging.debug(
                "ApplicationService.handleFocus: application is now in focus, nextResumeIsFirstActivity=$nextResumeIsFirstActivity",
            )
            nextResumeIsFirstActivity = false

            // We assume we are called *after* the notification module has determined entry due to notification.
            if (entryState != AppEntryAction.NOTIFICATION_CLICK) {
                entryState = AppEntryAction.APP_OPEN
            }

            applicationLifecycleNotifier.fire { it.onFocus(false) }
        } else {
            Logging.debug("ApplicationService.handleFocus: application never lost focus")
        }
    }
}
