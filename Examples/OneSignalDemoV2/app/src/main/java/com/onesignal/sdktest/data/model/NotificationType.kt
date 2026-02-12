package com.onesignal.sdktest.data.model

/**
 * Enum representing different types of push notifications that can be sent.
 */
enum class NotificationType(
    val title: String,
    val notificationTitle: String,
    val notificationBody: String,
    val bigPicture: String? = null,
    val largeIcon: String? = null
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
        // Use known working URLs from Firebase storage (same as V1 sample app)
        bigPicture = "https://i.ytimg.com/vi/C8YBKBuX43Q/maxresdefault.jpg",
        largeIcon = "https://pbs.twimg.com/profile_images/719602655337656321/kQUzR2Es_400x400.jpg"
    )
}
