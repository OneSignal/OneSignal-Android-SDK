package com.onesignal.onesignal.notification.internal

import androidx.core.app.NotificationCompat
import com.onesignal.onesignal.core.internal.common.time.ITime
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.notification.BackgroundImageLayout
import com.onesignal.onesignal.notification.IActionButton
import com.onesignal.onesignal.notification.IMutableNotification
import com.onesignal.onesignal.notification.INotification
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
open class Notification : INotification {
    var notificationExtender: NotificationCompat.Extender? = null

    /**
     * Summary notifications grouped
     * Notification payload will have the most recent notification received.
     */
    var groupedNotifications: List<Notification>? = null

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

    var rawPayload: String? = null

    constructor() {}
    constructor(payload: JSONObject, time: ITime) : this(null, payload, 0, time) {}
    constructor(
        groupedNotifications: List<Notification>?,
        jsonPayload: JSONObject,
        androidNotificationId: Int,
        time: ITime
    ) {
        initPayloadData(jsonPayload, time)
        this.groupedNotifications = groupedNotifications
        this.androidNotificationId = androidNotificationId
    }

    constructor(notification: Notification) {
        notificationExtender = notification.notificationExtender
        groupedNotifications = notification.groupedNotifications
        androidNotificationId = notification.androidNotificationId
        notificationId = notification.notificationId
        templateName = notification.templateName
        templateId = notification.templateId
        title = notification.title
        body = notification.body
        additionalData = notification.additionalData
        smallIcon = notification.smallIcon
        largeIcon = notification.largeIcon
        bigPicture = notification.bigPicture
        smallIconAccentColor = notification.smallIconAccentColor
        launchURL = notification.launchURL
        sound = notification.sound
        ledColor = notification.ledColor
        lockScreenVisibility = notification.lockScreenVisibility
        groupKey = notification.groupKey
        groupMessage = notification.groupMessage
        actionButtons = notification.actionButtons
        fromProjectNumber = notification.fromProjectNumber
        backgroundImageLayout = notification.backgroundImageLayout
        collapseId = notification.collapseId
        priority = notification.priority
        rawPayload = notification.rawPayload
        sentTime = notification.sentTime
        ttl = notification.ttl
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
                currentTime
            ) / 1000
            ttl = currentJsonPayload.optInt(
                NotificationConstants.GOOGLE_TTL_KEY,
                NotificationConstants.DEFAULT_TTL_IF_NOT_IN_PAYLOAD
            )
        } else if (currentJsonPayload.has(NotificationConstants.HMS_TTL_KEY)) {
            sentTime = currentJsonPayload.optLong(
                NotificationConstants.HMS_SENT_TIME_KEY,
                currentTime
            ) / 1000
            ttl = currentJsonPayload.optInt(
                NotificationConstants.HMS_TTL_KEY,
                NotificationConstants.DEFAULT_TTL_IF_NOT_IN_PAYLOAD
            )
        } else {
            sentTime = currentTime / 1000
            ttl = NotificationConstants.DEFAULT_TTL_IF_NOT_IN_PAYLOAD
        }
        notificationId = customJson.optString("i")
        templateId = customJson.optString("ti")
        templateName = customJson.optString("tn")
        rawPayload = currentJsonPayload.toString()
        additionalData = customJson.optJSONObject(NotificationConstants.PUSH_ADDITIONAL_DATA_KEY)
        launchURL = customJson.optString("u", null)
        body = currentJsonPayload.optString("alert", null)
        title = currentJsonPayload.optString("title", null)
        smallIcon = currentJsonPayload.optString("sicon", null)
        bigPicture = currentJsonPayload.optString("bicon", null)
        largeIcon = currentJsonPayload.optString("licon", null)
        sound = currentJsonPayload.optString("sound", null)
        groupKey = currentJsonPayload.optString("grp", null)
        groupMessage = currentJsonPayload.optString("grp_msg", null)
        smallIconAccentColor = currentJsonPayload.optString("bgac", null)
        ledColor = currentJsonPayload.optString("ledc", null)
        val visibility = currentJsonPayload.optString("vis", null)
        if (visibility != null) lockScreenVisibility = visibility.toInt()
        fromProjectNumber = currentJsonPayload.optString("from", null)
        priority = currentJsonPayload.optInt("pri", 0)
        val collapseKey = currentJsonPayload.optString("collapse_key", null)
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
            var actionBtns = mutableListOf<IActionButton>()
            for (i in 0 until jsonActionButtons.length()) {
                val jsonActionButton = jsonActionButtons.getJSONObject(i)
                val actionButton = ActionButton()
                actionButton.id = jsonActionButton.optString("id", null)
                actionButton.text = jsonActionButton.optString("text", null)
                actionButton.icon = jsonActionButton.optString("icon", null)
                actionBtns.add(actionButton)
            }
            actionButtons = actionBtns
            additionalData!!.remove(NotificationConstants.GENERATE_NOTIFICATION_BUNDLE_KEY_ACTION_ID)
            additionalData!!.remove("actionButtons")
        }
    }

    @Throws(Throwable::class)
    private fun setBackgroundImageLayoutFromData(currentJsonPayload: JSONObject) {
        val jsonStrBgImage = currentJsonPayload.optString("bg_img", null)
        if (jsonStrBgImage != null) {
            val jsonBgImage = JSONObject(jsonStrBgImage)
            backgroundImageLayout = BackgroundImageLayout(
                            jsonBgImage.optString("img"),
                            jsonBgImage.optString("tc"),
                            jsonBgImage.optString("bc"))
        }
    }

    override fun mutableCopy(): IMutableNotification {
        return MutableNotification(this)
    }

    fun copy(): Notification {
        return OSNotificationBuilder()
            .setNotificationExtender(notificationExtender)
            .setGroupedNotifications(groupedNotifications)
            .setAndroidNotificationId(androidNotificationId)
            .setNotificationId(notificationId)
            .setTemplateName(templateName)
            .setTemplateId(templateId)
            .setTitle(title)
            .setBody(body)
            .setAdditionalData(additionalData)
            .setSmallIcon(smallIcon)
            .setLargeIcon(largeIcon)
            .setBigPicture(bigPicture)
            .setSmallIconAccentColor(smallIconAccentColor)
            .setLaunchURL(launchURL)
            .setSound(sound)
            .setLedColor(ledColor)
            .setLockScreenVisibility(lockScreenVisibility)
            .setGroupKey(groupKey)
            .setGroupMessage(groupMessage)
            .setActionButtons(actionButtons)
            .setFromProjectNumber(fromProjectNumber)
            .setBackgroundImageLayout(backgroundImageLayout)
            .setCollapseId(collapseId)
            .setPriority(priority)
            .setRawPayload(rawPayload)
            .setSenttime(sentTime)
            .setTTL(ttl)
            .build()
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

    /**
     * List of action buttons on the notification.
     */
    class ActionButton : IActionButton {
        override var id: String? = null
        override var text: String? = null
        override var icon: String? = null

        constructor() {}
        constructor(jsonObject: JSONObject) {
            id = jsonObject.optString("id")
            text = jsonObject.optString("text")
            icon = jsonObject.optString("icon")
        }

        constructor(id: String?, text: String?, icon: String?) {
            this.id = id
            this.text = text
            this.icon = icon
        }

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

    class OSNotificationBuilder {
        private var notificationExtender: NotificationCompat.Extender? = null
        private var groupedNotifications: List<Notification>? = null
        private var androidNotificationId = 0
        private var notificationId: String? = null
        private var templateName: String? = null
        private var templateId: String? = null
        private var title: String? = null
        private var body: String? = null
        private var additionalData: JSONObject? = null
        private var smallIcon: String? = null
        private var largeIcon: String? = null
        private var bigPicture: String? = null
        private var smallIconAccentColor: String? = null
        private var launchURL: String? = null
        private var sound: String? = null
        private var ledColor: String? = null
        private var lockScreenVisibility = 1
        private var groupKey: String? = null
        private var groupMessage: String? = null
        private var actionButtons: List<IActionButton>? = null
        private var fromProjectNumber: String? = null
        private var backgroundImageLayout: BackgroundImageLayout? = null
        private var collapseId: String? = null
        private var priority = 0
        private var rawPayload: String? = null
        private var sentTime: Long = 0
        private var ttl = 0
        fun setNotificationExtender(notificationExtender: NotificationCompat.Extender?): OSNotificationBuilder {
            this.notificationExtender = notificationExtender
            return this
        }

        fun setGroupedNotifications(groupedNotifications: List<Notification>?): OSNotificationBuilder {
            this.groupedNotifications = groupedNotifications
            return this
        }

        fun setAndroidNotificationId(androidNotificationId: Int): OSNotificationBuilder {
            this.androidNotificationId = androidNotificationId
            return this
        }

        fun setNotificationId(notificationId: String?): OSNotificationBuilder {
            this.notificationId = notificationId
            return this
        }

        fun setTemplateName(templateName: String?): OSNotificationBuilder {
            this.templateName = templateName
            return this
        }

        fun setTemplateId(templateId: String?): OSNotificationBuilder {
            this.templateId = templateId
            return this
        }

        fun setTitle(title: String?): OSNotificationBuilder {
            this.title = title
            return this
        }

        fun setBody(body: String?): OSNotificationBuilder {
            this.body = body
            return this
        }

        fun setAdditionalData(additionalData: JSONObject?): OSNotificationBuilder {
            this.additionalData = additionalData
            return this
        }

        fun setSmallIcon(smallIcon: String?): OSNotificationBuilder {
            this.smallIcon = smallIcon
            return this
        }

        fun setLargeIcon(largeIcon: String?): OSNotificationBuilder {
            this.largeIcon = largeIcon
            return this
        }

        fun setBigPicture(bigPicture: String?): OSNotificationBuilder {
            this.bigPicture = bigPicture
            return this
        }

        fun setSmallIconAccentColor(smallIconAccentColor: String?): OSNotificationBuilder {
            this.smallIconAccentColor = smallIconAccentColor
            return this
        }

        fun setLaunchURL(launchURL: String?): OSNotificationBuilder {
            this.launchURL = launchURL
            return this
        }

        fun setSound(sound: String?): OSNotificationBuilder {
            this.sound = sound
            return this
        }

        fun setLedColor(ledColor: String?): OSNotificationBuilder {
            this.ledColor = ledColor
            return this
        }

        fun setLockScreenVisibility(lockScreenVisibility: Int): OSNotificationBuilder {
            this.lockScreenVisibility = lockScreenVisibility
            return this
        }

        fun setGroupKey(groupKey: String?): OSNotificationBuilder {
            this.groupKey = groupKey
            return this
        }

        fun setGroupMessage(groupMessage: String?): OSNotificationBuilder {
            this.groupMessage = groupMessage
            return this
        }

        fun setActionButtons(actionButtons: List<IActionButton>?): OSNotificationBuilder {
            this.actionButtons = actionButtons
            return this
        }

        fun setFromProjectNumber(fromProjectNumber: String?): OSNotificationBuilder {
            this.fromProjectNumber = fromProjectNumber
            return this
        }

        fun setBackgroundImageLayout(backgroundImageLayout: BackgroundImageLayout?): OSNotificationBuilder {
            this.backgroundImageLayout = backgroundImageLayout
            return this
        }

        fun setCollapseId(collapseId: String?): OSNotificationBuilder {
            this.collapseId = collapseId
            return this
        }

        fun setPriority(priority: Int): OSNotificationBuilder {
            this.priority = priority
            return this
        }

        fun setRawPayload(rawPayload: String?): OSNotificationBuilder {
            this.rawPayload = rawPayload
            return this
        }

        fun setSenttime(sentTime: Long): OSNotificationBuilder {
            this.sentTime = sentTime
            return this
        }

        fun setTTL(ttl: Int): OSNotificationBuilder {
            this.ttl = ttl
            return this
        }

        fun build(): Notification {
            val payload = Notification()
            payload.notificationExtender = notificationExtender
            payload.groupedNotifications = groupedNotifications
            payload.androidNotificationId = androidNotificationId
            payload.notificationId = notificationId
            payload.templateName = templateName
            payload.templateId = templateId
            payload.title = title
            payload.body = body
            payload.additionalData = additionalData
            payload.smallIcon = smallIcon
            payload.largeIcon = largeIcon
            payload.bigPicture = bigPicture
            payload.smallIconAccentColor = smallIconAccentColor
            payload.launchURL = launchURL
            payload.sound = sound
            payload.ledColor = ledColor
            payload.lockScreenVisibility = lockScreenVisibility
            payload.groupKey = groupKey
            payload.groupMessage = groupMessage
            payload.actionButtons = actionButtons
            payload.fromProjectNumber = fromProjectNumber
            payload.backgroundImageLayout = backgroundImageLayout
            payload.collapseId = collapseId
            payload.priority = priority
            payload.rawPayload = rawPayload
            payload.sentTime = sentTime
            payload.ttl = ttl
            return payload
        }
    }
}