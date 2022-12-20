package com.onesignal.notifications

import org.json.JSONObject

interface INotification {
    /**
     * Android notification id. Can later be used to dismiss the notification programmatically.
     */
    val androidNotificationId: Int

    /**
     * The OneSignal notification id.
     */
    val notificationId: String?

    /**
     * The name of the OneSignal template that created this notification. If no template was
     * used, this will be null.
     */
    val templateName: String?

    /**
     * The id of the OneSignal tempalte that created this notification. If no template was used,
     * this will be null.
     */
    val templateId: String?

    /**
     * The title displayed to the user.
     */
    val title: String?

    /**
     * The body displayed to the user.
     */
    val body: String?

    /**
     * The key/value custom additional data specified when creating the notification.
     */
    val additionalData: JSONObject?

    /**
     * The small icon information specified when creating the notification.
     */
    val smallIcon: String?

    /**
     * The large icon information specified when creating the notification.
     */
    val largeIcon: String?

    /**
     * The big picture information specified when creating the notification.
     */
    val bigPicture: String?

    /**
     * The accent color of the small icon specified when creating the notification.
     */
    val smallIconAccentColor: String?

    /**
     * The launch URL information specified when creating the notification.
     */
    val launchURL: String?

    /**
     * The sound information specified when creating the notification.
     */
    val sound: String?

    /**
     * The LED color information specified when creating the notification.
     */
    val ledColor: String?

    /**
     * The lock screen visibility information specified when creating the notification.
     */
    val lockScreenVisibility: Int

    /**
     * The group key information specified when creating the notification.
     */
    val groupKey: String?

    /**
     * The group message information specified when creating the notification.
     */
    val groupMessage: String?

    /**
     * The action buttons specified when creating the notification.
     */
    val actionButtons: List<IActionButton>?

    /**
     * The from project information specified when creating the notification.
     */
    val fromProjectNumber: String?

    /**
     * The background image layout information specified when creating the notification.
     */
    @Deprecated("This is not applicable for Android 12+")
    val backgroundImageLayout: BackgroundImageLayout?

    /**
     * The collapse ID specified when creating the notification.
     */
    val collapseId: String?

    /**
     * The priority information specified when creating the notification.
     */
    val priority: Int

    /**
     * When this notification was sent by the backend.
     */
    val sentTime: Long

    /**
     * The TTL information specified when creating the notification.
     */
    val ttl: Int

    /**
     * Create a mutable copy of this notification. Typically used in [IRemoteNotificationReceivedHandler]
     * or [INotificationWillShowInForegroundHandler] to modify an incoming notification prior to it
     * being displayed to the user.
     */
    fun mutableCopy(): IMutableNotification
}
