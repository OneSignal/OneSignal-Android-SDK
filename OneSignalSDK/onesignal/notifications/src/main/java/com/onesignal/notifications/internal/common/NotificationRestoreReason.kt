package com.onesignal.notifications.internal.common

/** Why a notification is being shown again. */
internal enum class NotificationRestoreReason {
    /** Android cleared the shade, such as after a reboot, app update, or force-stop. */
    SHADE_RESTORE,

    /**
     * A group dropped to one member. Shown as a standalone notification, still in the shade,
     * so the app's extender still applies.
     */
    GROUP_REGROUP,
}
