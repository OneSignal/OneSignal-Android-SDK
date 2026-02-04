package com.onesignal.sdktest.data.model

import com.onesignal.sdktest.R

/**
 * Enum representing different types of in-app messages that can be triggered.
 */
enum class InAppMessageType(
    val title: String,
    val iconResId: Int,
    val triggerKey: String,
    val triggerValue: String
) {
    TOP_BANNER(
        title = "Top Banner",
        iconResId = R.drawable.ic_top_banner,
        triggerKey = "iam_type",
        triggerValue = "top_banner"
    ),
    BOTTOM_BANNER(
        title = "Bottom Banner",
        iconResId = R.drawable.ic_bottom_banner,
        triggerKey = "iam_type",
        triggerValue = "bottom_banner"
    ),
    CENTER_MODAL(
        title = "Center Modal",
        iconResId = R.drawable.ic_center_modal,
        triggerKey = "iam_type",
        triggerValue = "center_modal"
    ),
    FULL_SCREEN(
        title = "Full Screen",
        iconResId = R.drawable.ic_full_screen,
        triggerKey = "iam_type",
        triggerValue = "full_screen"
    )
}
