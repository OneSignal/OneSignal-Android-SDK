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
        title = "Top",
        triggerKey = "iam_type",
        triggerValue = "top_banner",
    ),
    CENTER_MODAL(
        title = "Center",
        triggerKey = "iam_type",
        triggerValue = "center_modal",
    ),
    BOTTOM_BANNER(
        title = "Bottom",
        triggerKey = "iam_type",
        triggerValue = "bottom_banner",
    ),
    FULL_SCREEN(
        title = "Full",
        triggerKey = "iam_type",
        triggerValue = "full_screen",
    ),
    CAROUSEL(
        title = "Carousel",
        triggerKey = "iam_type",
        triggerValue = "carousel",
    ),
    HTML(
        title = "HTML",
        triggerKey = "iam_type",
        triggerValue = "html",
    ),
    FULL_BLEED(
        title = "Full Bleed",
        triggerKey = "iam_type",
        triggerValue = "full_bleed",
    ),
    CAROUSEL_BLEED(
        title = "Carousel Bleed",
        triggerKey = "iam_type",
        triggerValue = "carousel_bleed",
    )
}
