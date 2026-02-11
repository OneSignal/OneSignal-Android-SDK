package com.onesignal.sdktest.data.model

/**
 * Enum representing different types of in-app messages that can be triggered.
 */
enum class InAppMessageType(
    val title: String,
    val triggerKey: String,
    val triggerValue: String
) {
    TOP_BANNER(
        title = "Top Banner",
        triggerKey = "iam_type",
        triggerValue = "top_banner"
    ),
    BOTTOM_BANNER(
        title = "Bottom Banner",
        triggerKey = "iam_type",
        triggerValue = "bottom_banner"
    ),
    CENTER_MODAL(
        title = "Center Modal",
        triggerKey = "iam_type",
        triggerValue = "center_modal"
    ),
    FULL_SCREEN(
        title = "Full Screen",
        triggerKey = "iam_type",
        triggerValue = "full_screen"
    )
}
