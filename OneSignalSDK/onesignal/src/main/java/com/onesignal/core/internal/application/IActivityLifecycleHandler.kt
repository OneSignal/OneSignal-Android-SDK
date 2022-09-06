package com.onesignal.core.internal.application

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks

/**
 * Implementations of the activity lifecycle handler added via [IApplicationService.addActivityLifecycleHandler]
 * will be notified throughout the activity lifecycle.  This should be used over the Android-provided
 * [ActivityLifecycleCallbacks], it provides an abstraction more specific to the OneSignal SDK
 */
internal interface IActivityLifecycleHandler {

    /**
     * Called when an activity is made available to the application.
     */
    fun onActivityAvailable(activity: Activity)

    /**
     * Called when an activity has been stopped on an application.
     */
    fun onActivityStopped(activity: Activity)
}

/**
 * A base implementation of [IActivityLifecycleHandler] that is a no-op. Concrete implementations
 * can use this if they only want to override a subset of the callbacks that make up this interface.
 */
internal open class ActivityLifecycleHandlerBase : IActivityLifecycleHandler {
    override fun onActivityAvailable(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
}