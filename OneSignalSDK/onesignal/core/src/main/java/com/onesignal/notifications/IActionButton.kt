package com.onesignal.notifications

/**
 * An action button within an [INotification]
 */
interface IActionButton {
    /**
     * The ID of the action button specified when creating the notification.
     */
    val id: String?

    /**
     * The text displayed on the action button.
     */
    val text: String?

    /**
     * The icon displayed on the action button.
     */
    val icon: String?
}
