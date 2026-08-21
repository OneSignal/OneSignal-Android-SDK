package com.onesignal.notifications

/**
 * The state of an Android notification channel on the current device.
 */
enum class NotificationChannelState {
    /**
     * The channel exists and is enabled.
     */
    ENABLED,

    /**
     * The channel exists but is disabled, either directly or through its channel group.
     */
    DISABLED,

    /**
     * The channel does not exist on the device.
     */
    NOT_FOUND,

    /**
     * Notification channels are not supported on this device.
     */
    NOT_SUPPORTED,

    /**
     * The channel state could not be determined.
     */
    UNKNOWN,
}
