package com.onesignal.onesignal.internal.application

import android.app.Activity
import android.content.Context

interface IApplicationService  {

    /**
     * The application context
     */
    val appContext: Context?

    /**
     * The current activity for the application
     */
    var current: Activity?

    /**
     * Add an activity available handler
     */
    fun addActivityLifecycleHandler(handler: IActivityLifecycleHandler)

    /**
     * Remove an activity lifecycle handler
     */
    fun removeActivityLifecycleHandler(handler: IActivityLifecycleHandler)

    /**
     * Add an application available handler
     */
    fun addApplicationLifecycleHandler(handler: IApplicationLifecycleHandler)

    /**
     * Remove an application lifecycle handler
     */
    fun removeApplicationLifecycleHandler(handler: IApplicationLifecycleHandler)

    /**
     * Add a handler that will be notified when the system condition
     * has changed, will be automatically removed after called one time.
     */
    fun addSystemConditionHandler(handler: ISystemConditionHandler)
}
