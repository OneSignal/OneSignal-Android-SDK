package com.onesignal.notifications.internal.common

/** Why a notification is being shown again. */
internal enum class NotificationRestoreReason {
    /** Android cleared the shade, such as after a reboot, app update, or force-stop. */
    SHADE_RESTORE,

    /**
     * A group dropped to one member, which is re-posted so it looks standalone. Normally still
     * in the shade. The extender applies, but channel and sound are forced back to the silent
     * restore channel, the same as any restore.
     */
    GROUP_REGROUP,
}
