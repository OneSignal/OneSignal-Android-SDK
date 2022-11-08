package com.onesignal.notifications

import org.json.JSONObject

/**
 * This exception is thrown by [INotificationsManager.postNotification] when
 * the request failed for some reason.
 */
class PostNotificationException(
    /**
     * The response payload received as part of the unsuccessful request.
     */
    val json: JSONObject
) : Exception()
