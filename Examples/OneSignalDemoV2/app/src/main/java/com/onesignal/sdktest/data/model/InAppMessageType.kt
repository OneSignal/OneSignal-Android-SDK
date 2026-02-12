package com.onesignal.sdktest.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Enum representing different types of in-app messages that can be triggered.
 */
enum class InAppMessageType(
    val title: String,
    val triggerKey: String,
    val triggerValue: String,
    val icon: ImageVector
) {
    TOP_BANNER(
        title = "Top Banner",
        triggerKey = "iam_type",
        triggerValue = "top_banner",
        icon = Icons.Filled.VerticalAlignTop
    ),
    BOTTOM_BANNER(
        title = "Bottom Banner",
        triggerKey = "iam_type",
        triggerValue = "bottom_banner",
        icon = Icons.Filled.VerticalAlignBottom
    ),
    CENTER_MODAL(
        title = "Center Modal",
        triggerKey = "iam_type",
        triggerValue = "center_modal",
        icon = Icons.Filled.CropSquare
    ),
    FULL_SCREEN(
        title = "Full Screen",
        triggerKey = "iam_type",
        triggerValue = "full_screen",
        icon = Icons.Filled.Fullscreen
    )
}
