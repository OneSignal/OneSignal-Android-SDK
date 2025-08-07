package com.onesignal.core.internal.application

import android.app.Activity
import android.content.Context

/**
 * The application service provides the ability to view various application-specific
 * information and subscribe to application events.
 */
interface IApplicationService {
    /**
     * The application context
     */
    val appContext: Context

    /**
     * The current activity for the application. When null the application has no
     * active activity, it is in the background.
     */
    val current: Activity?

    /**
     * Whether the application is currently in the foreground.
     */
    val isInForeground: Boolean

    /**
     * Whether the device storage for the user data is accessible.
     *
     * Always return true for API 23 and below as the device booted into an unlocked state by default
     */
    val isDeviceStorageUnlocked: Boolean

    /**
     * How the application was entered.  This is writeable to allow for the setting
     * to [AppEntryAction.NOTIFICATION_CLICK] when it is determined a notification
     * drove the app entry.
     */
    var entryState: AppEntryAction

    /**
     * Will determine and suspend until system conditions are available for displaying
     * UI to the user.
     */
    suspend fun waitUntilSystemConditionsAvailable(): Boolean

    /**
     * Will determine and suspend until the decor view is ready to displayed
     * within.
     */
    suspend fun waitUntilActivityReady(): Boolean

    /**
     * Add an activity lifecycle available handler.
     */
    fun addActivityLifecycleHandler(handler: IActivityLifecycleHandler)

    /**
     * Remove an activity lifecycle handler.
     */
    fun removeActivityLifecycleHandler(handler: IActivityLifecycleHandler)

    /**
     * Add an application available handler.
     */
    fun addApplicationLifecycleHandler(handler: IApplicationLifecycleHandler)

    /**
     * Remove an application lifecycle handler.
     */
    fun removeApplicationLifecycleHandler(handler: IApplicationLifecycleHandler)
}
