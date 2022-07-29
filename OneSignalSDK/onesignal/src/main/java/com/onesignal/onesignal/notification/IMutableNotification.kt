package com.onesignal.onesignal.notification

import androidx.core.app.NotificationCompat
import org.json.JSONObject

interface IMutableNotification : INotification {

    override var androidNotificationId: Int
    override var notificationId: String?
    override var templateName: String?
    override var templateId: String?
    override var title: String?
    override var body: String?
    override var additionalData: JSONObject?
    override var smallIcon: String?
    override var largeIcon: String?
    override var bigPicture: String?
    override var smallIconAccentColor: String?
    override var launchURL: String?
    override var sound: String?
    override var ledColor: String?
    override var lockScreenVisibility: Int
    override var groupKey: String?
    override var groupMessage: String?
    override var actionButtons: List<IActionButton>?
    override var fromProjectNumber: String?
    override var backgroundImageLayout: BackgroundImageLayout?
    override var collapseId: String?
    override var priority: Int
    override var sentTime: Long
    override var ttl: Int

    /**
     * If a developer wants to override the data within a received notification, they can do so by
     * creating a [NotificationCompat.Extender] within the [OneSignal.OSRemoteNotificationReceivedHandler]
     * and override any notification data desired
     * <br></br><br></br>
     * @see OneSignal.OSRemoteNotificationReceivedHandler
     */
    fun setExtender(extender: NotificationCompat.Extender?)
}