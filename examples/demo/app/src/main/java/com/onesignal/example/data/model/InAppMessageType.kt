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
        title = "TOP",
        triggerKey = "iam_type",
        triggerValue = "top_banner",
    ),
    BOTTOM_BANNER(
        title = "BOTTOM",
        triggerKey = "iam_type",
        triggerValue = "bottom_banner",
    ),
    CENTER_MODAL(
        title = "CENTER",
        triggerKey = "iam_type",
        triggerValue = "center_modal",
    ),
    HTML(
        title = "HTML",
        triggerKey = "iam_type",
        triggerValue = "html",
    ),
    FULL_SCREEN(
        title = "FULL",
        triggerKey = "iam_type",
        triggerValue = "full_screen",
    ),
    FULL_BLEED(
        title = "FULL BLEED",
        triggerKey = "iam_type",
        triggerValue = "full_bleed",
    ),
    CAROUSEL(
        title = "CAROUSEL",
        triggerKey = "iam_type",
        triggerValue = "carousel",
    ),
    CAROUSEL_BLEED(
        title = "CAROUSEL BLEED",
        triggerKey = "iam_type",
        triggerValue = "carousel_bleed",
    )
}
