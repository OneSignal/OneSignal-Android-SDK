package com.onesignal.notifications.internal

import com.onesignal.core.internal.time.ITime
import com.onesignal.notifications.INotificationClickEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.json.JSONObject

class NotificationClickEventTests : FunSpec({

    val time =
        mockk<ITime> {
            every { currentTimeMillis } returns 1_000L
        }

    fun notification(
        id: String,
        title: String? = null,
    ) = Notification(
        JSONObject()
            .put("custom", JSONObject().put("i", id).toString())
            .put("title", title),
        time,
    )

    test("serializes the notification and click result using the wrapper SDK shape") {
        val groupedNotification = notification("grouped-id", "Grouped")
        val notification =
            notification("notification-id", "Opened").apply {
                androidNotificationId = 42
                body = "Body"
                additionalData = JSONObject().put("customKey", "customValue")
                actionButtons = listOf(Notification.ActionButton("button-id", "Open", "icon"))
                groupedNotifications = listOf(groupedNotification)
            }
        val event: INotificationClickEvent =
            NotificationClickEvent(
                notification,
                NotificationClickResult("button-id", "https://onesignal.com"),
            )

        val json = event.toJSONObject()
        val notificationJson = json.getJSONObject("notification")
        val resultJson = json.getJSONObject("result")

        notificationJson.getInt("androidNotificationId") shouldBe 42
        notificationJson.getString("notificationId") shouldBe "notification-id"
        notificationJson.getString("title") shouldBe "Opened"
        notificationJson.getString("body") shouldBe "Body"
        notificationJson.getJSONObject("additionalData").getString("customKey") shouldBe "customValue"
        notificationJson.getJSONArray("actionButtons").getJSONObject(0).getString("id") shouldBe "button-id"
        val groupedNotificationJson =
            notificationJson.getJSONArray("groupedNotifications").getJSONObject(0)
        groupedNotificationJson.getString("notificationId") shouldBe "grouped-id"
        notificationJson.getJSONObject("rawPayload").getString("title") shouldBe "Opened"
        groupedNotificationJson.getJSONObject("rawPayload").getString("title") shouldBe "Grouped"
        resultJson.getString("actionId") shouldBe "button-id"
        resultJson.getString("url") shouldBe "https://onesignal.com"
    }

    test("omits nullable fields when they are not set") {
        val event: INotificationClickEvent =
            NotificationClickEvent(
                notification("notification-id"),
                NotificationClickResult(null, null),
            )

        val json = event.toJSONObject()
        val notificationJson = json.getJSONObject("notification")
        val resultJson = json.getJSONObject("result")

        notificationJson.has("title") shouldBe false
        notificationJson.has("actionButtons") shouldBe false
        notificationJson.getJSONArray("groupedNotifications").length() shouldBe 0
        resultJson.has("actionId") shouldBe false
        resultJson.has("url") shouldBe false
    }
})
