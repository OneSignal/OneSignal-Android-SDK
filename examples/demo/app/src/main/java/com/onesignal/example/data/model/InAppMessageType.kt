package com.onesignal.example.data.model

/**
 * Enum representing different types of in-app messages that can be triggered.
 */
enum class InAppMessageType(
    val title: String,
    val triggerKey: String,
    val triggerValue: String,
) {
    TOP_BANNER(
        title = "TOP BANNER",
        triggerKey = "iam_type",
        triggerValue = "top_banner",
    ),
    BOTTOM_BANNER(
        title = "BOTTOM BANNER",
        triggerKey = "iam_type",
        triggerValue = "bottom_banner",
    ),
    CENTER_MODAL(
        title = "CENTER MODAL",
        triggerKey = "iam_type",
        triggerValue = "center_modal",
    ),
    FULL_SCREEN(
        title = "FULL SCREEN",
        triggerKey = "iam_type",
        triggerValue = "full_screen",
    )
}
