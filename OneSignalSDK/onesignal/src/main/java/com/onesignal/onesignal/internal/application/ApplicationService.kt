package com.onesignal.onesignal.internal.application

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.ComponentCallbacks
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import com.onesignal.onesignal.internal.common.AndroidUtils
import com.onesignal.onesignal.internal.common.EventProducer
import com.onesignal.onesignal.internal.common.IEventProducer
import com.onesignal.onesignal.logging.Logging
import java.lang.ref.WeakReference

class ApplicationService(
    private val _activityLifecycleNotifier: IEventProducer<IActivityLifecycleHandler> = EventProducer(),
    private val _applicationLifecycleNotifier: IEventProducer<IApplicationLifecycleHandler> = EventProducer(),
    private val _systemConditionNotifier: IEventProducer<ISystemConditionHandler> = EventProducer()
) : IApplicationService, ActivityLifecycleCallbacks, OnGlobalLayoutListener  {

    /** The application the context is owned by **/
    private var _application: Application? = null

    /** The current activity **/
    private var _current: Activity? = null

    /** Whether the application is in the foreground **/
    private var _inForeground: Boolean = false

    /** Whether the next resume is due to the first activity or not **/
    private var _nextResumeIsFirstActivity: Boolean = false

    private var _activityReferences = 0
    private var _isActivityChangingConfigurations = false

    override var appContext: Context? = null

    override var current: Activity?
            get() = _current
            set(value) {
                _current = value

                Logging.debug("_current is NOW: " + if (_current != null) "" + _current?.javaClass?.name + ":" + _current else "null")

                _activityLifecycleNotifier.fire { it.onAvailable(value) }

                if(value != null) {
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
        appContext = context
        if(_application == null) {
            _application = context.applicationContext as Application
            _application!!.registerActivityLifecycleCallbacks(this)

            val configuration = object : ComponentCallbacks {
                override fun onConfigurationChanged(newConfig: Configuration) {
                    // If Activity contains the configChanges orientation flag, re-create the view this way
                    if (_current != null && AndroidUtils.hasConfigChangeFlag(
                            _current!!,
                            ActivityInfo.CONFIG_ORIENTATION
                        )
                    ) {
                        onOrientationChanged(newConfig.orientation, _current!!)
                    }
                }

                override fun onLowMemory() {}
            }

            _application!!.registerComponentCallbacks(configuration)
        }

        val isContextActivity = context is Activity
        val isCurrentActivityNull = _current == null

        _inForeground = !isCurrentActivityNull || isContextActivity
        Logging.debug("ApplicationService.init: inForeground=$_inForeground")

        if (_inForeground) {
            if (isCurrentActivityNull && isContextActivity) {
                _current = context as Activity?
                _nextResumeIsFirstActivity = true
            }
        } else {
            _nextResumeIsFirstActivity = true
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
        if (_current != null)
            handler.onAvailable(_current)
    }

    override fun removeActivityLifecycleHandler(handler: IActivityLifecycleHandler) {
        _activityLifecycleNotifier.unsubscribe(handler)
    }

    override fun addSystemConditionHandler(handler: ISystemConditionHandler) {
        _systemConditionNotifier.subscribe(handler)
    }

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {
        Logging.debug("ApplicationService.onActivityCreated: $activity")
    }

    override fun onActivityStarted(activity: Activity) {
        Logging.debug("ApplicationService.onActivityStarted: $activity")

        _current = activity

        if (++_activityReferences == 1 && !_isActivityChangingConfigurations) {
            handleFocus()
        }
    }

    override fun onActivityResumed(activity: Activity) {
        Logging.debug("ApplicationService.onActivityResumed: $activity")
    }

    override fun onActivityPaused(activity: Activity) {
        Logging.debug("ApplicationService.onActivityPaused: $activity")
    }

    override fun onActivityStopped(activity: Activity) {
        Logging.debug("ApplicationService.onActivityStopped: $activity")

        _isActivityChangingConfigurations = activity.isChangingConfigurations
        if (--_activityReferences == 0 && !_isActivityChangingConfigurations) {
            _current = null
            handleLostFocus()
        }

        _activityLifecycleNotifier.fire { it.onStopped(activity) }
    }

    override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {
        // Intentionally left empty
    }

    override fun onActivityDestroyed(activity: Activity) {
        Logging.debug("ApplicationService.onActivityDestroyed: $activity")
    }

    override fun onGlobalLayout() {
        val keyboardUp = AndroidUtils.isKeyboardUp(WeakReference(_current))
        if (!keyboardUp) {
            _systemConditionNotifier.fireThenRemove { it.systemConditionChanged() }
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
        _activityLifecycleNotifier.fire { it.onStopped(activity) }

        // Show view
        _activityLifecycleNotifier.fire { it.onAvailable(activity) }

        activity.window.decorView.viewTreeObserver.addOnGlobalLayoutListener(this)

        handleFocus()
    }

    private fun handleLostFocus() {
        if (_inForeground) {
            Logging.debug("ApplicationService.handleLostFocus: application is now out of focus")

            // TODO: Should we spin up a worker task for this? Or rely on the listeners to do it.
            _inForeground = false;

            _applicationLifecycleNotifier.fire { it.onUnfocused() }
        }
        else {
            Logging.debug("ApplicationService.handleLostFocus: application already out of focus")
        }
    }

    private fun handleFocus() {
        if (!_inForeground || _nextResumeIsFirstActivity) {
            Logging.debug("ApplicationService.handleFocus: application is now in focus, nextResumeIsFirstActivity=$_nextResumeIsFirstActivity")
            _nextResumeIsFirstActivity = false
            _inForeground = true
            _applicationLifecycleNotifier.fire { it.onFocus() }
        } else {
            Logging.debug("ApplicationService.handleFocus: application never lost focus")
            // TODO: Do we need to fire something to cancel the unfocus processing?
        }
    }
}
