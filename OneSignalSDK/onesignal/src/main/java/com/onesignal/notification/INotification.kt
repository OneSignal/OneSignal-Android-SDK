package com.onesignal.notification

import org.json.JSONObject

interface INotification {
    /** Android notification id. Can later be used to dismiss the notification programmatically. */
    val androidNotificationId: Int

    /** The OneSignal notification id.  **/
    val notificationId: String?

    /**
     * The name of the OneSignal template that created this notification. If no template was
     * used, this will be null.
     **/
    val templateName: String?

    /**
     * The id of the OneSignal tempalte that created this notification. If no template was used,
     * this will be null.
     */
    val templateId: String?

    /** The title displayed to the user. */
    val title: String?

    /** The body displayed to the user. */
    val body: String?

    /** The key/value custom additional data specified when creating the notification. */
    val additionalData: JSONObject?
    val smallIcon: String?
    val largeIcon: String?
    val bigPicture: String?
    val smallIconAccentColor: String?
    val launchURL: String?
    val sound: String?
    val ledColor: String?
    val lockScreenVisibility: Int
    val groupKey: String?
    val groupMessage: String?
    val actionButtons: List<IActionButton>?
    val fromProjectNumber: String?
    val backgroundImageLayout: BackgroundImageLayout?
    val collapseId: String?
    val priority: Int
    val sentTime: Long
    val ttl: Int

    fun mutableCopy(): IMutableNotification
}
