package com.onesignal.core.internal.application.impl

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.ComponentCallbacks
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
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
import com.onesignal.core.internal.application.OneSignalInternalActivity
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.delay
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

class ApplicationService() : IApplicationService, ActivityLifecycleCallbacks, OnGlobalLayoutListener {
    companion object {
        @Volatile
        private var sharedInstance: ApplicationService? = null

        /**
         * Process-wide instance shared between [ActivityLifecycleInitializer] (which registers
         * lifecycle observation at process start) and dependency injection (which resolves the
         * service later, during SDK init). Both must reference the same object so the activity
         * lifecycle observed before init is visible to the running SDK.
         */
        fun getInstance(): ApplicationService =
            sharedInstance ?: synchronized(this) {
                sharedInstance ?: ApplicationService().also { sharedInstance = it }
            }

        /**
         * The process-wide instance only if [ActivityLifecycleInitializer] already created one;
         * never creates it. Dependency injection uses this so that when the startup initializer did
         * not run (its provider was disabled, or unit tests), each SDK init gets its own instance
         * rather than a leaked process-wide one.
         */
        fun getInstanceOrNull(): ApplicationService? = sharedInstance
    }

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

    /**
     * Activities whose [onActivityStarted] we have counted toward [activityReferences] and have not
     * yet stopped. Stored as weak references so a forgotten activity cannot be retained by the SDK.
     * Guards [onActivityStopped] from decrementing for an activity whose start was never observed,
     * which happens when the SDK initializes late (after the first activity is already running) or
     * when a transient trampoline stops without its start having been counted.
     */
    private val startedActivities: MutableSet<Activity> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    /** True once the activity lifecycle observer has been registered with the [Application]. */
    private var lifecycleObserverInstalled = false

    private val componentCallbacks =
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

    private val wasInBackground: Boolean
        get() = !isInForeground || nextResumeIsFirstActivity

    /**
     * Register activity lifecycle observation with the [Application]. Safe to call multiple times;
     * only the first call registers. Invoked at process start by [ActivityLifecycleInitializer] so
     * the SDK observes the first activity's full lifecycle regardless of when [start] is later
     * called.
     */
    fun attachToApplication(application: Application) {
        if (lifecycleObserverInstalled) {
            return
        }
        lifecycleObserverInstalled = true

        if (_appContext == null) {
            _appContext = application
        }

        application.registerActivityLifecycleCallbacks(this)
        application.registerComponentCallbacks(componentCallbacks)
    }

    /**
     * Call to "start" this service, expected to be called during initialization of the SDK.
     *
     * @param context The context the SDK has been initialized under.
     */
    fun start(context: Context) {
        val needsFallbackSeeding = !lifecycleObserverInstalled

        // The runtime init context wins for appContext (callers, including tests, may pass a
        // wrapping context). The pre-init observer only needs the Application for registration.
        _appContext = context

        attachToApplication(context.applicationContext as Application)

        if (needsFallbackSeeding) {
            // The lifecycle observer was not installed at process start (e.g. the androidx.startup
            // provider was disabled, or in unit tests). Seed focus state from the init context so
            // late initialization still establishes the current activity and entry state.
            seedFocusFromInitContext(context)
        }

        Logging.debug("ApplicationService.init: entryState=$entryState")
    }

    private fun seedFocusFromInitContext(context: Context) {
        val isContextActivity = context is Activity
        val isCurrentActivityNull = current == null

        if (!isCurrentActivityNull || isContextActivity) {
            entryState = AppEntryAction.APP_OPEN
            if (isCurrentActivityNull && isContextActivity) {
                val activity = context as Activity
                current = activity
                startedActivities.add(activity)
                activityReferences = 1
                nextResumeIsFirstActivity = false
            }
        } else {
            nextResumeIsFirstActivity = true
            entryState = AppEntryAction.APP_CLOSE
        }
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

        // Re-entrant start for the activity that is already current and still counted: nothing to do.
        // The membership check is essential: a notification-open trampoline can briefly stop the host
        // and then resume the *same* host instance. That stop removed the host from startedActivities
        // (decrementing the count) without clearing current, so this start must fall through to
        // re-count it. Returning here would leave the count permanently short — every later host stop
        // would see !wasCounted and onUnfocused would never fire again.
        if (current == activity && startedActivities.contains(activity)) {
            return
        }

        // SDK-internal activities (notification-open trampolines) are counted toward
        // activityReferences so the count stays balanced (a trampoline often lives in its own task,
        // briefly stopping the host), but they must never become current, take focus, or change
        // entry state — those are owned by real activities and the notification module.
        val isInternal = activity is OneSignalInternalActivity

        // Set by the preceding onActivityStopped when an activity is recreated for a config change
        // (e.g. rotation). That stop skipped the decrement, so this replacement start must not
        // increment — otherwise the reference count climbs on every rotation and focus is never lost.
        val recreatedAfterConfigChange = isActivityChangingConfigurations

        if (!isInternal && current == null && !recreatedAfterConfigChange) {
            // No foreground activity was present: a cold start, or a warm start after the app was
            // backgrounded. This first real activity start is a foreground entry, so arm the
            // first-activity flag. This forces wasInBackground true even when the notification module
            // has already set entryState to NOTIFICATION_CLICK (which otherwise reads as foreground),
            // ensuring onFocus fires. handleFocus preserves the NOTIFICATION_CLICK entry state.
            nextResumeIsFirstActivity = true
        }

        if (!isInternal && current != activity) {
            current = activity
        }

        val isNewlyStarted = startedActivities.add(activity)

        if (!isInternal && wasInBackground && !isActivityChangingConfigurations) {
            // Real entry into the foreground; re-baseline the counter against this activity.
            startedActivities.clear()
            startedActivities.add(activity)
            activityReferences = 1
            handleFocus()
        } else if (!isInternal && recreatedAfterConfigChange) {
            isActivityChangingConfigurations = false
        } else if (isNewlyStarted) {
            activityReferences++
        }
    }

    override fun onActivityResumed(activity: Activity) {
        Logging.debug("ApplicationService.onActivityResumed($activityReferences,$entryState): $activity")

        if (activity is OneSignalInternalActivity) {
            return
        }

        // When an activity has something shown above it, it will be paused allowing
        // the new activity to be started (where current is set).  However when that
        // new activity is finished the original activity is simply resumed (it's
        // already been created).  For this case, we make sure current is set
        // to the now current activity.
        if (current != activity) {
            current = activity
        }

        if (wasInBackground && !isActivityChangingConfigurations) {
            startedActivities.clear()
            startedActivities.add(activity)
            activityReferences = 1
            handleFocus()
        }
    }

    override fun onActivityPaused(activity: Activity) {
        Logging.debug("ApplicationService.onActivityPaused($activityReferences,$entryState): $activity")
    }

    override fun onActivityStopped(activity: Activity) {
        Logging.debug("ApplicationService.onActivityStopped($activityReferences,$entryState): $activity")

        val isInternal = activity is OneSignalInternalActivity

        if (!isInternal) {
            isActivityChangingConfigurations = activity.isChangingConfigurations
        }

        if (isInternal || !isActivityChangingConfigurations) {
            decrementStartedActivity(activity, isInternal)
        }

        activityLifecycleNotifier.fire { it.onActivityStopped(activity) }
    }

    private fun decrementStartedActivity(
        activity: Activity,
        isInternal: Boolean,
    ) {
        // Only decrement for an activity whose start we previously counted. A late-init SDK can
        // observe a transient activity's stop without ever seeing its start; counting that would
        // underflow the reference count and falsely drop app focus.
        val wasCounted = startedActivities.remove(activity)
        if (!wasCounted || --activityReferences > 0) {
            // An internal trampoline can reach onStop without its onStart ever being counted — e.g. a
            // wrapper SDK that disabled the process-start initializer, so the trampoline started
            // before the lifecycle observer attached. If it finishes with nothing in the foreground,
            // the NOTIFICATION_CLICK reset below would otherwise be skipped, leaving entry state stale.
            resetStaleNotificationEntryIfBackgrounded(isInternal)
            return
        }

        activityReferences = 0

        if (!isInternal || current != null) {
            // Either a real activity was the last counted one to stop, or a trampoline finished while
            // a real host was still current. The latter happens on a foreground tap of a URL/
            // suppressed notification: the open launches a browser (or nothing) instead of the host,
            // so the host stops while the trampoline is on top (its decrement deferred) and current
            // is never cleared. The app is genuinely backgrounded now, so drop focus.
            current = null
            handleLostFocus()
            return
        }

        resetStaleNotificationEntryIfBackgrounded(isInternal)
    }

    /**
     * Reset a stale [AppEntryAction.NOTIFICATION_CLICK] left by an internal trampoline that finished
     * without a real activity ever taking focus (e.g. a URL notification that opened a browser).
     * Guarded on [current] being null so a foregrounded real activity is never affected. Does not
     * fire onUnfocused — no focus was ever held — so a later organic open is not mis-attributed as a
     * direct notification session.
     */
    private fun resetStaleNotificationEntryIfBackgrounded(isInternal: Boolean) {
        if (isInternal && current == null && entryState == AppEntryAction.NOTIFICATION_CLICK) {
            entryState = AppEntryAction.APP_CLOSE
        }
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
