package com.onesignal.sdktest.data.model

import com.onesignal.sdktest.R

/**
 * Enum representing different types of push notifications that can be sent.
 */
enum class NotificationType(
    val title: String,
    val iconResId: Int,
    val notificationTitle: String,
    val notificationBody: String
) {
    GENERAL(
        title = "General",
        iconResId = R.drawable.ic_bell_white_24dp,
        notificationTitle = "General Notification",
        notificationBody = "This is a general notification"
    ),
    GREETINGS(
        title = "Greetings",
        iconResId = R.drawable.ic_human_greeting_white_24dp,
        notificationTitle = "Hello!",
        notificationBody = "Welcome to our app"
    ),
    PROMOTIONS(
        title = "Promotions",
        iconResId = R.drawable.ic_brightness_percent_white_24dp,
        notificationTitle = "Special Offer!",
        notificationBody = "Check out our latest promotions"
    ),
    BREAKING_NEWS(
        title = "Breaking News",
        iconResId = R.drawable.ic_newspaper_white_24dp,
        notificationTitle = "Breaking News",
        notificationBody = "Important news update"
    ),
    ABANDONED_CART(
        title = "Abandoned Cart",
        iconResId = R.drawable.ic_cart_white_24dp,
        notificationTitle = "You left something behind!",
        notificationBody = "Complete your purchase"
    ),
    NEW_POST(
        title = "New Post",
        iconResId = R.drawable.ic_image_white_24dp,
        notificationTitle = "New Post Available",
        notificationBody = "Check out the latest content"
    ),
    RE_ENGAGEMENT(
        title = "Re-Engagement",
        iconResId = R.drawable.ic_gesture_tap_white_24dp,
        notificationTitle = "We miss you!",
        notificationBody = "Come back and explore"
    ),
    RATING(
        title = "Rating",
        iconResId = R.drawable.ic_star_white_24dp,
        notificationTitle = "Rate Us",
        notificationBody = "Please rate our app"
    )
}
