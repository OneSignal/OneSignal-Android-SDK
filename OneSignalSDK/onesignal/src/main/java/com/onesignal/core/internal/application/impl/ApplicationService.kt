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
import com.onesignal.common.events.IEventProducer
import com.onesignal.common.threading.Waiter
import com.onesignal.core.internal.application.ActivityLifecycleHandlerBase
import com.onesignal.core.internal.application.AppEntryAction
import com.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.internal.logging.Logging
import java.lang.ref.WeakReference

class ApplicationService() : IApplicationService, ActivityLifecycleCallbacks, OnGlobalLayoutListener {
    private val _activityLifecycleNotifier: IEventProducer<IActivityLifecycleHandler> = EventProducer()
    private val _applicationLifecycleNotifier: IEventProducer<IApplicationLifecycleHandler> = EventProducer()
    private val _systemConditionNotifier: IEventProducer<ISystemConditionHandler> = EventProducer()

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
                _activityLifecycleNotifier.fire { it.onActivityAvailable(value) }
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
    private var _nextResumeIsFirstActivity: Boolean = false

    /** Used to determine when an app goes in focus and out of focus **/
    private var _activityReferences = 0
    private var _isActivityChangingConfigurations = false

    /**
     * Call to "start" this service, expected to be called during initialization of the SDK.
     *
     * @param context The context the SDK has been initialized under.
     */
    fun start(context: Context) {
        _appContext = context

        val application = context.applicationContext as Application
        application.registerActivityLifecycleCallbacks(this)

        val configuration = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                // If Activity contains the configChanges orientation flag, re-create the view this way
                if (current != null && AndroidUtils.hasConfigChangeFlag(
                        current!!,
                        ActivityInfo.CONFIG_ORIENTATION
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
                _activityReferences = 1
                _nextResumeIsFirstActivity = true
            }
        } else {
            _nextResumeIsFirstActivity = true
            entryState = AppEntryAction.APP_CLOSE
        }

        Logging.debug("ApplicationService.init: entryState=$entryState")
    }

    override fun addApplicationLifecycleHandler(handler: IApplicationLifecycleHandler) {
        _applicationLifecycleNotifier.subscribe(handler)
    }

    override fun removeApplicationLifecycleHandler(handler: IApplicationLifecycleHandler) {
        _applicationLifecycleNotifier.unsubscribe(handler)
    }

    override fun addActivityLifecycleHandler(handler: IActivityLifecycleHandler) {
        _activityLifecycleNotifier.subscribe(handler)
        if (current != null) {
            handler.onActivityAvailable(current!!)
        }
    }

    override fun removeActivityLifecycleHandler(handler: IActivityLifecycleHandler) {
        _activityLifecycleNotifier.unsubscribe(handler)
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        Logging.debug("ApplicationService.onActivityCreated: $activity")
    }

    override fun onActivityStarted(activity: Activity) {
        Logging.debug("ApplicationService.onActivityStarted: $activity")

        current = activity

        if (++_activityReferences == 1 && !_isActivityChangingConfigurations) {
            handleFocus()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        Logging.debug("ApplicationService.onActivityResumed: $activity")

        // When an activity has something shown above it, it will be paused allowing
        // the new activity to be started (where current is set).  However when that
        // new activity is finished the original activity is simply resumed (it's
        // already been created).  For this case, we make sure current is set
        // to the now current activity.
        if (current != activity) {
            current = activity
        }
    }

    override fun onActivityPaused(activity: Activity) {
        Logging.debug("ApplicationService.onActivityPaused: $activity")
    }

    override fun onActivityStopped(activity: Activity) {
        Logging.debug("ApplicationService.onActivityStopped: $activity")

        _isActivityChangingConfigurations = activity.isChangingConfigurations
        if (--_activityReferences == 0 && !_isActivityChangingConfigurations) {
            current = null
            handleLostFocus()
        }

        _activityLifecycleNotifier.fire { it.onActivityStopped(activity) }
    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
        // Intentionally left empty
    }

    override fun onActivityDestroyed(activity: Activity) {
        Logging.debug("ApplicationService.onActivityDestroyed: $activity")
    }

    override fun onGlobalLayout() {
        _systemConditionNotifier.fire { it.systemConditionChanged() }
    }

    override suspend fun waitUntilSystemConditionsAvailable(): Boolean {
        val currentActivity = current
        if (currentActivity == null) {
            Logging.warn("ApplicationService.waitUntilSystemConditionsAvailable: current is null")
            return false
        }

        try {
            // Detect if user has a dialog fragment showing. If so we suspend until that dialog goes away
            // We cannot detect AlertDialogs because they are added to the decor view as linear layout without an identification
            if (currentActivity is AppCompatActivity) {
                val manager = currentActivity.supportFragmentManager

                var lastFragment = manager.fragments.lastOrNull()
                if (lastFragment != null && lastFragment.isVisible && lastFragment is DialogFragment) {
                    val waiter = Waiter()

                    manager.registerFragmentLifecycleCallbacks(
                        object : FragmentManager.FragmentLifecycleCallbacks() {
                            override fun onFragmentDetached(fm: FragmentManager, fragmentDetached: Fragment) {
                                super.onFragmentDetached(fm, fragmentDetached)
                                if (fragmentDetached is DialogFragment) {
                                    manager.unregisterFragmentLifecycleCallbacks(this)
                                    waiter.wake()
                                }
                            }
                        },
                        true
                    )

                    waiter.waitForWake()
                }
            }
        } catch (exception: NoClassDefFoundError) {
            Logging.info("ApplicationService.waitUntilSystemConditionsAvailable: AppCompatActivity is not used in this app, skipping 'isDialogFragmentShowing' check: $exception")
        }

        val waiter = Waiter()
        val systemConditionHandler = object : ISystemConditionHandler {
            override fun systemConditionChanged() {
                val keyboardUp = DeviceUtils.isKeyboardUp(WeakReference(current))
                if (!keyboardUp) {
                    waiter.wake()
                }
            }
        }

        // Add the listener prior to checking the condition to avoid a race condition where
        // we'll never get a callback and will be waiting forever.
        _systemConditionNotifier.subscribe(systemConditionHandler)
        val keyboardUp = DeviceUtils.isKeyboardUp(WeakReference(currentActivity))
        // if the keyboard is up we suspend until it is down
        if (keyboardUp) {
            Logging.warn("ApplicationService.waitUntilSystemConditionsAvailable: keyboard up detected")
            waiter.waitForWake()
        }
        _systemConditionNotifier.unsubscribe(systemConditionHandler)

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
    fun decorViewReady(activity: Activity, runnable: Runnable) {
        val listenerKey = "decorViewReady:$runnable"
        val self = this
        activity.window.decorView.post {
            self.addActivityLifecycleHandler(object : ActivityLifecycleHandlerBase() {
                override fun onActivityAvailable(currentActivity: Activity) {
                    self.removeActivityLifecycleHandler(this)
                    if (AndroidUtils.isActivityFullyReady(currentActivity)) {
                        runnable.run()
                    } else {
                        decorViewReady(currentActivity, runnable)
                    }
                }
            })
        }
    }

    /**
     * Takes pieces from onActivityResumed and onActivityStopped to recreate the view when the
     * phones orientation is changed from manual detection using the onConfigurationChanged callback
     * This fix was originally implemented for In App Messages not being re-shown when orientation
     * was changed on wrapper SDK apps
     */
    private fun onOrientationChanged(orientation: Int, activity: Activity) {
        // Log device orientation change
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Logging.debug("ApplicationService.onOrientationChanged: Configuration Orientation Change: LANDSCAPE ($orientation) on activity: $activity")
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            Logging.debug("ApplicationService.onOrientationChanged: Configuration Orientation Change: PORTRAIT ($orientation) on activity: $activity")
        }

        // Remove view
        handleLostFocus()
        _activityLifecycleNotifier.fire { it.onActivityStopped(activity) }

        // Show view
        _activityLifecycleNotifier.fire { it.onActivityAvailable(activity) }

        activity.window.decorView.viewTreeObserver.addOnGlobalLayoutListener(this)

        handleFocus()
    }

    private fun handleLostFocus() {
        if (isInForeground) {
            Logging.debug("ApplicationService.handleLostFocus: application is now out of focus")

            entryState = AppEntryAction.APP_CLOSE

            _applicationLifecycleNotifier.fire { it.onUnfocused() }
        } else {
            Logging.debug("ApplicationService.handleLostFocus: application already out of focus")
        }
    }

    private fun handleFocus() {
        if (!isInForeground || _nextResumeIsFirstActivity) {
            Logging.debug("ApplicationService.handleFocus: application is now in focus, nextResumeIsFirstActivity=$_nextResumeIsFirstActivity")
            _nextResumeIsFirstActivity = false

            // We assume we are called *after* the notification module has determined entry due to notification.
            if (entryState != AppEntryAction.NOTIFICATION_CLICK) {
                entryState = AppEntryAction.APP_OPEN
            }

            _applicationLifecycleNotifier.fire { it.onFocus() }
        } else {
            Logging.debug("ApplicationService.handleFocus: application never lost focus")
        }
    }
}
