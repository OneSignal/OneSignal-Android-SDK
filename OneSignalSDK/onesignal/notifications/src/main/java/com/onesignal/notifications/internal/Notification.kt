package com.onesignal.notifications.internal

import androidx.core.app.NotificationCompat
import com.onesignal.common.safeJSONObject
import com.onesignal.common.safeString
import com.onesignal.common.threading.Waiter
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.BackgroundImageLayout
import com.onesignal.notifications.IActionButton
import com.onesignal.notifications.IDisplayableMutableNotification
import com.onesignal.notifications.internal.common.NotificationConstants
import com.onesignal.notifications.internal.common.NotificationHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The notification the user received
 * <br></br><br></br>
 * [.androidNotificationId] - Android Notification ID assigned to the notification. Can be used to cancel or replace the notification
 * [.groupedNotifications] - If the notification is a summary notification for a group, this will contain
 * all notification payloads it was created from.
 */
class Notification : IDisplayableMutableNotification {
    var notificationExtender: NotificationCompat.Extender? = null
    val displayWaiter: Waiter = Waiter()

    override var groupedNotifications: List<Notification>? = null
    override var androidNotificationId = 0
    override var notificationId: String? = null
    override var templateName: String? = null
    override var templateId: String? = null
    override var title: String? = null
    override var body: String? = null
    override var additionalData: JSONObject? = null
    override var smallIcon: String? = null
    override var largeIcon: String? = null
    override var bigPicture: String? = null
    override var smallIconAccentColor: String? = null
    override var launchURL: String? = null
    override var sound: String? = null
    override var ledColor: String? = null
    override var lockScreenVisibility = 1
    override var groupKey: String? = null
    override var groupMessage: String? = null
    override var actionButtons: List<IActionButton>? = null
    override var fromProjectNumber: String? = null
    override var backgroundImageLayout: BackgroundImageLayout? = null
    override var collapseId: String? = null
    override var priority = 0
    override var sentTime: Long = 0
    override var ttl = 0
    override var rawPayload: String = ""

    constructor(payload: JSONObject, time: ITime) : this(null, payload, 0, time) {}
    constructor(
        groupedNotifications: List<Notification>?,
        jsonPayload: JSONObject,
        androidNotificationId: Int,
        time: ITime,
    ) {
        initPayloadData(jsonPayload, time)
        this.groupedNotifications = groupedNotifications
        this.androidNotificationId = androidNotificationId
    }

    private fun initPayloadData(currentJsonPayload: JSONObject, time: ITime) {
        val customJson: JSONObject = try {
            NotificationHelper.getCustomJSONObject(currentJsonPayload)
        } catch (t: Throwable) {
            Logging.error("Error assigning OSNotificationReceivedEvent payload values!", t)
            return
        }
        val currentTime = time.currentTimeMillis
        if (currentJsonPayload.has(NotificationConstants.GOOGLE_TTL_KEY)) {
            sentTime = currentJsonPayload.optLong(
                NotificationConstants.GOOGLE_SENT_TIME_KEY,
                currentTime,
            ) / 1000
            ttl = currentJsonPayload.optInt(
                NotificationConstants.GOOGLE_TTL_KEY,
                NotificationConstants.DEFAULT_TTL_IF_NOT_IN_PAYLOAD,
            )
        } else if (currentJsonPayload.has(NotificationConstants.HMS_TTL_KEY)) {
            sentTime = currentJsonPayload.optLong(
                NotificationConstants.HMS_SENT_TIME_KEY,
                currentTime,
            ) / 1000
            ttl = currentJsonPayload.optInt(
                NotificationConstants.HMS_TTL_KEY,
                NotificationConstants.DEFAULT_TTL_IF_NOT_IN_PAYLOAD,
            )
        } else {
            sentTime = currentTime / 1000
            ttl = NotificationConstants.DEFAULT_TTL_IF_NOT_IN_PAYLOAD
        }
        notificationId = customJson.safeString("i")
        templateId = customJson.safeString("ti")
        templateName = customJson.safeString("tn")
        rawPayload = currentJsonPayload.toString()
        additionalData = customJson.safeJSONObject(NotificationConstants.PUSH_ADDITIONAL_DATA_KEY)
        launchURL = customJson.safeString("u")
        body = currentJsonPayload.safeString("alert")
        title = currentJsonPayload.safeString("title")
        smallIcon = currentJsonPayload.safeString("sicon")
        bigPicture = currentJsonPayload.safeString("bicon")
        largeIcon = currentJsonPayload.safeString("licon")
        sound = currentJsonPayload.safeString("sound")
        groupKey = currentJsonPayload.safeString("grp")
        groupMessage = currentJsonPayload.safeString("grp_msg")
        smallIconAccentColor = currentJsonPayload.safeString("bgac")
        ledColor = currentJsonPayload.safeString("ledc")
        val visibility = currentJsonPayload.safeString("vis")
        if (visibility != null) lockScreenVisibility = visibility.toInt()
        fromProjectNumber = currentJsonPayload.safeString("from")
        priority = currentJsonPayload.optInt("pri", 0)
        val collapseKey = currentJsonPayload.safeString("collapse_key")
        if ("do_not_collapse" != collapseKey) collapseId = collapseKey
        try {
            setActionButtonsFromData()
        } catch (t: Throwable) {
            Logging.error("Error assigning OSNotificationReceivedEvent.actionButtons values!", t)
        }
        try {
            setBackgroundImageLayoutFromData(currentJsonPayload)
        } catch (t: Throwable) {
            Logging.error("Error assigning OSNotificationReceivedEvent.backgroundImageLayout values!", t)
        }
    }

    @Throws(Throwable::class)
    private fun setActionButtonsFromData() {
        if (additionalData != null && additionalData!!.has("actionButtons")) {
            val jsonActionButtons = additionalData!!.getJSONArray("actionButtons")
            val actionBtns = mutableListOf<IActionButton>()
            for (i in 0 until jsonActionButtons.length()) {
                val jsonActionButton = jsonActionButtons.getJSONObject(i)
                val actionButton = ActionButton(
                                    jsonActionButton.safeString("id"),
                                    jsonActionButton.safeString("text"),
                                    jsonActionButton.safeString("icon"))
                actionBtns.add(actionButton)
            }
            actionButtons = actionBtns
            additionalData!!.remove(NotificationConstants.GENERATE_NOTIFICATION_BUNDLE_KEY_ACTION_ID)
            additionalData!!.remove("actionButtons")
        }
    }

    @Throws(Throwable::class)
    private fun setBackgroundImageLayoutFromData(currentJsonPayload: JSONObject) {
        val jsonStrBgImage = currentJsonPayload.safeString("bg_img")
        if (jsonStrBgImage != null) {
            val jsonBgImage = JSONObject(jsonStrBgImage)
            backgroundImageLayout = BackgroundImageLayout(
                jsonBgImage.safeString("img"),
                jsonBgImage.safeString("tc"),
                jsonBgImage.safeString("bc"),
            )
        }
    }

    override fun setExtender(extender: NotificationCompat.Extender?) {
        notificationExtender = extender
    }

    fun hasNotificationId(): Boolean {
        return androidNotificationId != 0
    }

    fun toJSONObject(): JSONObject {
        val mainObj = JSONObject()
        try {
            mainObj.put("androidNotificationId", androidNotificationId)
            val payloadJsonArray = JSONArray()
            if (groupedNotifications != null) {
                for (notification in groupedNotifications!!) payloadJsonArray.put(notification.toJSONObject())
            }
            mainObj.put("groupedNotifications", payloadJsonArray)
            mainObj.put("notificationId", notificationId)
            mainObj.put("templateName", templateName)
            mainObj.put("templateId", templateId)
            mainObj.put("title", title)
            mainObj.put("body", body)
            mainObj.put("smallIcon", smallIcon)
            mainObj.put("largeIcon", largeIcon)
            mainObj.put("bigPicture", bigPicture)
            mainObj.put("smallIconAccentColor", smallIconAccentColor)
            mainObj.put("launchURL", launchURL)
            mainObj.put("sound", sound)
            mainObj.put("ledColor", ledColor)
            mainObj.put("lockScreenVisibility", lockScreenVisibility)
            mainObj.put("groupKey", groupKey)
            mainObj.put("groupMessage", groupMessage)
            mainObj.put("fromProjectNumber", fromProjectNumber)
            mainObj.put("collapseId", collapseId)
            mainObj.put("priority", priority)
            if (additionalData != null) mainObj.put("additionalData", additionalData)
            if (actionButtons != null) {
                val actionButtonJsonArray = JSONArray()
                for (actionButton in actionButtons!!) {
                    actionButtonJsonArray.put((actionButton as ActionButton).toJSONObject())
                }
                mainObj.put("actionButtons", actionButtonJsonArray)
            }
            mainObj.put("rawPayload", rawPayload)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return mainObj
    }

    override fun toString(): String {
        return "OSNotification{" +
            "notificationExtender=" + notificationExtender +
            ", groupedNotifications=" + groupedNotifications +
            ", androidNotificationId=" + androidNotificationId +
            ", notificationId='" + notificationId + '\'' +
            ", templateName='" + templateName + '\'' +
            ", templateId='" + templateId + '\'' +
            ", title='" + title + '\'' +
            ", body='" + body + '\'' +
            ", additionalData=" + additionalData +
            ", smallIcon='" + smallIcon + '\'' +
            ", largeIcon='" + largeIcon + '\'' +
            ", bigPicture='" + bigPicture + '\'' +
            ", smallIconAccentColor='" + smallIconAccentColor + '\'' +
            ", launchURL='" + launchURL + '\'' +
            ", sound='" + sound + '\'' +
            ", ledColor='" + ledColor + '\'' +
            ", lockScreenVisibility=" + lockScreenVisibility +
            ", groupKey='" + groupKey + '\'' +
            ", groupMessage='" + groupMessage + '\'' +
            ", actionButtons=" + actionButtons +
            ", fromProjectNumber='" + fromProjectNumber + '\'' +
            ", backgroundImageLayout=" + backgroundImageLayout +
            ", collapseId='" + collapseId + '\'' +
            ", priority=" + priority +
            ", rawPayload='" + rawPayload + '\'' +
            '}'
    }

    override fun display() {
        displayWaiter.wake()
    }

    /**
     * List of action buttons on the notification.
     */
    class ActionButton(
        override val id: String? = null,
        override val text: String? = null,
        override val icon: String? = null
    ) : IActionButton {
        fun toJSONObject(): JSONObject {
            val json = JSONObject()
            try {
                json.put("id", id)
                json.put("text", text)
                json.put("icon", icon)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            return json
        }
    }
}
