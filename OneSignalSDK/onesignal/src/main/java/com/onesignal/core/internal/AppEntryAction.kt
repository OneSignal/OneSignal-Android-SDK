package com.onesignal.core.internal

/**
 * An app entry type enum for knowing how the user foregrounded or backgrounded the app.
 * <br></br><br></br>
 * The enum also helps decide the type of session the user is in an is tracked in [OneSignal.sessionManager]
 * from the [OSSessionManager].
 * <br></br><br></br>
 * [AppEntryAction.NOTIFICATION_CLICK] will always lead a overridden [com.onesignal.influence.domain.OSInfluenceType.DIRECT].
 * [AppEntryAction.APP_OPEN] on a new session notifications within the attribution window
 * parameter, this will lead to a [com.onesignal.influence.domain.OSInfluenceType.DIRECT].
 * <br></br><br></br>
 * @see OneSignal.onAppFocus
 *
 * @see OneSignal.onAppLostFocus
 *
 * @see OneSignal.handleNotificationOpen
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
