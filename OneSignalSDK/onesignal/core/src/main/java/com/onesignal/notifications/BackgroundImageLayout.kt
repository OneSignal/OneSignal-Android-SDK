package com.onesignal.notifications

/**
 * Background image layout information for a [INotification].
 */
@Deprecated("This is not applicable for Android 12+")
class BackgroundImageLayout(
    /**
     * The asset file, android resource name, or URL to remote image.
     */
    val image: String? = null,
    /**
     * The title text color.
     */
    val titleTextColor: String? = null,
    /**
     * The body text color.
     */
    val bodyTextColor: String? = null,
)
