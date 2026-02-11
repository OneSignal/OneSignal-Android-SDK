package com.onesignal.sdktest.data.model

/**
 * Enum representing different types of push notifications that can be sent.
 */
enum class NotificationType(
    val title: String,
    val notificationTitle: String,
    val notificationBody: String,
    val bigPicture: String? = null
) {
    SIMPLE(
        title = "Simple",
        notificationTitle = "Simple Notification",
        notificationBody = "This is a simple push notification"
    ),
    WITH_IMAGE(
        title = "With Image",
        notificationTitle = "Image Notification",
        notificationBody = "This notification includes an image",
        bigPicture = "https://img.onesignal.com/permanent/d1c17a59-a5c5-4e62-b376-2c8daa39bb44"
    )
}
