package com.onesignal.onesignal.core.internal.application.impl

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
import com.onesignal.onesignal.core.internal.AppEntryAction
import com.onesignal.onesignal.core.internal.application.IActivityLifecycleHandler
import com.onesignal.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.common.AndroidUtils
import com.onesignal.onesignal.core.internal.common.DeviceUtils
import com.onesignal.onesignal.core.internal.common.events.EventProducer
import com.onesignal.onesignal.core.internal.common.events.IEventProducer
import com.onesignal.onesignal.core.internal.logging.Logging
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import java.lang.ref.WeakReference

class ApplicationService() : IApplicationService, ActivityLifecycleCallbacks, OnGlobalLayoutListener  {
    private val _activityLifecycleNotifier: IEventProducer<IActivityLifecycleHandler> = EventProducer()
    private val _applicationLifecycleNotifier: IEventProducer<IApplicationLifecycleHandler> = EventProducer()
    private val _systemConditionNotifier: IEventProducer<ISystemConditionHandler> = EventProducer()

    /** The application the context is owned by **/
    private var _application: Application? = null

    /** The current activity **/
    private var _current: Activity? = null

    /** Whether the application is in the foreground **/
    override var isInForeground: Boolean = false

    /** How the application has been entered. **/
    override var entryState: AppEntryAction = AppEntryAction.APP_OPEN

    /** Whether the next resume is due to the first activity or not **/
    private var _nextResumeIsFirstActivity: Boolean = false

    private var _activityReferences = 0
    private var _isActivityChangingConfigurations = false

    override val appContext: Context
            get() = _appContext!!

    private var _appContext: Context? = null

    override var current: Activity?
            get() = _current
            set(value) {
                _current = value

                Logging.debug("_current is NOW: " + if (_current != null) "" + _current?.javaClass?.name + ":" + _current else "null")

                if(value != null) {
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

    fun start(context: Context)
    {
        _appContext = context
        if(_application == null) {
            _application = context.applicationContext as Application
            _application!!.registerActivityLifecycleCallbacks(this)

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

            _application!!.registerComponentCallbacks(configuration)
        }

        val isContextActivity = context is Activity
        val isCurrentActivityNull = current == null

        isInForeground = !isCurrentActivityNull || isContextActivity
        Logging.debug("ApplicationService.init: inForeground=$isInForeground")

        if (isInForeground) {
            entryState = AppEntryAction.APP_OPEN
            if (isCurrentActivityNull && isContextActivity) {
                current = context as Activity?
                _nextResumeIsFirstActivity = true
            }
        } else {
            _nextResumeIsFirstActivity = true
            entryState = AppEntryAction.APP_CLOSE
        }
    }

    override fun addApplicationLifecycleHandler(handler: IApplicationLifecycleHandler) {
        _applicationLifecycleNotifier.subscribe(handler)
    }

    override fun removeApplicationLifecycleHandler(handler: IApplicationLifecycleHandler) {
        _applicationLifecycleNotifier.unsubscribe(handler)
    }

    override fun addActivityLifecycleHandler(handler: IActivityLifecycleHandler) {
        _activityLifecycleNotifier.subscribe(handler)
        if (current != null)
            handler.onActivityAvailable(current!!)
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
        if(current != activity)
            current = activity
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

    override suspend fun waitUntilSystemConditionsAvailable() : Boolean {
        val currentActivity = current
        if (currentActivity == null) {
            Logging.warn("OSSystemConditionObserver curActivity null")
            return false
        }

        try {
            // Detect if user has a dialog fragment showing. If so we suspend until that dialog goes away
            // We cannot detect AlertDialogs because they are added to the decor view as linear layout without an identification
            if (currentActivity is AppCompatActivity) {
                val manager = currentActivity.supportFragmentManager

                var lastFragment = manager.fragments.lastOrNull()
                if (lastFragment != null && lastFragment.isVisible && lastFragment is DialogFragment) {
                    val channel = Channel<Any?>()

                    manager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
                        override fun onFragmentDetached(fm: FragmentManager, fragmentDetached: Fragment) {
                            super.onFragmentDetached(fm, fragmentDetached)
                            if (fragmentDetached is DialogFragment) {
                                manager.unregisterFragmentLifecycleCallbacks(this)
                                runBlocking {
                                    channel.send(null)
                                }
                            }
                        }
                    }, true)

                    channel.receive()
                }

            }
        } catch (exception: NoClassDefFoundError) {
            Logging.info("AppCompatActivity is not used in this app, skipping 'isDialogFragmentShowing' check: $exception")
        }

        val channel = Channel<Any?>()
        val systemConditionHandler = object : ISystemConditionHandler {
            override fun systemConditionChanged() {
                val keyboardUp = DeviceUtils.isKeyboardUp(WeakReference(current))
                if (!keyboardUp) {
                    runBlocking {
                        channel.send(null)
                    }
                }
            }
        }

        // Add the listener prior to checking the condition to avoid a race condition where
        // we'll never get a callback and will be waiting forever.
        _systemConditionNotifier.subscribe(systemConditionHandler)
        val keyboardUp = DeviceUtils.isKeyboardUp(WeakReference(currentActivity))
        // if the keyboard is up we suspend until it is down
        if(keyboardUp) {
            Logging.warn("OSSystemConditionObserver keyboard up detected")
            channel.receive()
        }
        _systemConditionNotifier.unsubscribe(systemConditionHandler)

        return true
    }

    override suspend fun waitUntilActivityReady(): Boolean {
        val currentActivity = current ?: return false

        val channel = Channel<Any?>()
        decorViewReady(currentActivity!!, object : Runnable {
            override fun run() = runBlocking {
                channel.send(null)
            }
        })

        channel.receive()
        return true
    }

    // Ensures the root decor view is ready by checking the following;
    //   1. Is fully attach to the root window and insets are available
    //   2. Ensure if any Activities are changed while waiting we use the updated one
    fun decorViewReady(activity: Activity, runnable: Runnable) {
        val listenerKey = "decorViewReady:$runnable"
        val self = this
        activity.window.decorView.post {
            self.addActivityLifecycleHandler(object : IActivityLifecycleHandler {
                override fun onActivityAvailable(currentActivity: Activity) {
                    self.removeActivityLifecycleHandler(this)
                    if (AndroidUtils.isActivityFullyReady(currentActivity))
                        runnable.run()
                    else decorViewReady(currentActivity, runnable)
                }

                override fun onActivityStopped(activity: Activity) {}
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
        if (orientation == Configuration.ORIENTATION_LANDSCAPE)
            Logging.debug("Configuration Orientation Change: LANDSCAPE ($orientation) on activity: $activity")
        else if (orientation == Configuration.ORIENTATION_PORTRAIT)
            Logging.debug("Configuration Orientation Change: PORTRAIT ($orientation) on activity: $activity")

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

            // TODO: Should we spin up a worker task for this? Or rely on the listeners to do it.
            isInForeground = false;
            entryState = AppEntryAction.APP_CLOSE

            _applicationLifecycleNotifier.fire { it.onUnfocused() }
        }
        else {
            Logging.debug("ApplicationService.handleLostFocus: application already out of focus")
        }
    }

    private fun handleFocus() {
        if (!isInForeground || _nextResumeIsFirstActivity) {
            Logging.debug("ApplicationService.handleFocus: application is now in focus, nextResumeIsFirstActivity=$_nextResumeIsFirstActivity")
            _nextResumeIsFirstActivity = false
            isInForeground = true
            _applicationLifecycleNotifier.fire { it.onFocus() }
        } else {
            Logging.debug("ApplicationService.handleFocus: application never lost focus")
            // TODO: Do we need to fire something to cancel the unfocus processing?
        }
    }
}
