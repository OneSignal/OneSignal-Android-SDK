package com.onesignal.core.internal.application

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks

internal interface IActivityLifecycleHandler {
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
open class ActivityLifecycleHandlerBase : IActivityLifecycleHandler {
    override fun onActivityAvailable(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
}
