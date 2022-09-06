package com.onesignal.core.internal.application

/**
 * An app entry type enum for knowing how the user foregrounded or backgrounded the app.
 */
internal enum class AppEntryAction {
    /**
     * Entered the app through opening a notification
     */
    NOTIFICATION_CLICK,

    /**
     * Entered the app through clicking the icon
     */
    APP_OPEN,

    /**
     * App came from the background
     */
    APP_CLOSE;

    val isNotificationClick: Boolean
        get() = this == NOTIFICATION_CLICK
    val isAppOpen: Boolean
        get() = this == APP_OPEN
    val isAppClose: Boolean
        get() = this == APP_CLOSE
}
