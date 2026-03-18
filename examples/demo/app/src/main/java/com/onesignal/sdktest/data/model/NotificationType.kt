package com.onesignal.sdktest.data.model

enum class NotificationType(
    val title: String,
    val notificationTitle: String,
    val notificationBody: String,
    val bigPicture: String? = null,
    val largeIcon: String? = null,
    val androidChannelId: String? = null
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
        bigPicture = "https://media.onesignal.com/automated_push_templates/ratings_template.png"
    ),
    WITH_SOUND(
        title = "With Sound",
        notificationTitle = "Sound Notification",
        notificationBody = "This notification plays a custom sound",
        androidChannelId = "b3b015d9-c050-4042-8548-dcc34aa44aa4"
    )
}
