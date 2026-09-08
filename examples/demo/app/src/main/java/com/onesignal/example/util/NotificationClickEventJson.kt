package com.onesignal.example.util

import com.onesignal.notifications.IActionButton
import com.onesignal.notifications.INotification
import com.onesignal.notifications.INotificationClickEvent
import org.json.JSONArray
import org.json.JSONObject

fun INotificationClickEvent.toJson(): JSONObject =
    JSONObject().apply {
        put("notification", notification.toJson())
        put(
            "result",
            JSONObject().apply {
                put("actionId", result.actionId)
                put("url", result.url)
            },
        )
    }

@Suppress("DEPRECATION")
private fun INotification.toJson(): JSONObject =
    JSONObject().apply {
        put("androidNotificationId", androidNotificationId)
        put("notificationId", notificationId)
        put("templateName", templateName)
        put("templateId", templateId)
        put("title", title)
        put("body", body)
        put("additionalData", additionalData)
        put("smallIcon", smallIcon)
        put("largeIcon", largeIcon)
        put("bigPicture", bigPicture)
        put("smallIconAccentColor", smallIconAccentColor)
        put("launchURL", launchURL)
        put("sound", sound)
        put("ledColor", ledColor)
        put("lockScreenVisibility", lockScreenVisibility)
        put("groupKey", groupKey)
        put("groupMessage", groupMessage)
        put("actionButtons", actionButtons.toActionButtonsJson())
        put("fromProjectNumber", fromProjectNumber)
        put(
            "backgroundImageLayout",
            backgroundImageLayout?.let {
                JSONObject()
                    .put("image", it.image)
                    .put("titleTextColor", it.titleTextColor)
                    .put("bodyTextColor", it.bodyTextColor)
            },
        )
        put("collapseId", collapseId)
        put("priority", priority)
        put("sentTime", sentTime)
        put("ttl", ttl)
        put("groupedNotifications", groupedNotifications.toNotificationsJson())
        put("rawPayload", rawPayload)
    }

private fun List<INotification>?.toNotificationsJson(): JSONArray =
    JSONArray().apply {
        this@toNotificationsJson?.forEach { put(it.toJson()) }
    }

private fun List<IActionButton>?.toActionButtonsJson(): JSONArray =
    JSONArray().apply {
        this@toActionButtonsJson?.forEach {
            put(
                JSONObject()
                    .put("id", it.id)
                    .put("text", it.text)
                    .put("icon", it.icon),
            )
        }
    }
