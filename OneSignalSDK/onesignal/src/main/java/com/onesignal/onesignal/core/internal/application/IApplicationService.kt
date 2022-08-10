package com.onesignal.onesignal.core.internal.application

import android.app.Activity
import android.content.Context

interface IApplicationService  {

    /**
     * The application context
     */
    val appContext: Context

    /**
     * The current activity for the application
     */
    val current: Activity?

    /**
     * Whether the app is currently in the foreground
     */
    val isInForeground: Boolean

    /**
     * Will determine and suspend until system conditions are available for displaying
     * UI to the user.
     */
    suspend fun waitUntilSystemConditionsAvailable() : Boolean

    /**
     * Will determine and suspend until the decor view is ready to displayed
     * within.
     */
    suspend fun waitUntilActivityReady() : Boolean

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
}
