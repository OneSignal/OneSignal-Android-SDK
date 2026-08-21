package com.onesignal.notifications.internal.common

/**
 * Why OneSignal is sending a notification through the generation pipeline again, having already
 * done so once when the notification first arrived.
 */
internal enum class NotificationRestoreReason {
    /**
     * Android dropped the notification from the shade and OneSignal is showing it again. Happens
     * after a reboot, an app update, or a cold start following a force stop.
     */
    SHADE_RESTORE,

    /**
     * A group dropped to one remaining member, so that member is rebuilt to render on its own
     * instead of inside a summary. The notification is still in the shade.
     *
     * See NotificationSummaryManager. The generation pipeline is what applies the app's extender,
     * so the rebuilt notification has to go back through it to keep the app's customizations.
     */
    GROUP_REGROUP,
}
