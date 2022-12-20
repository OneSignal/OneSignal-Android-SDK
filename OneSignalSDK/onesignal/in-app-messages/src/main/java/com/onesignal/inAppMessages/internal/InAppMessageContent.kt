package com.onesignal.inAppMessages.internal

import com.onesignal.common.safeBool
import com.onesignal.common.safeDouble
import com.onesignal.common.safeJSONObject
import com.onesignal.common.safeString
import com.onesignal.inAppMessages.internal.display.impl.WebViewManager
import org.json.JSONObject

internal open class InAppMessageContent constructor(jsonObject: JSONObject) {
    var contentHtml: String? = null
    var useHeightMargin: Boolean = true
    var useWidthMargin: Boolean = true
    var isFullBleed: Boolean = false

    // The following properties are populated from Javascript events
    var displayLocation: WebViewManager.Position? = null
    var displayDuration: Double? = null
    var pageHeight: Int = 0

    init {
        contentHtml = jsonObject.safeString(HTML)
        displayDuration = jsonObject.safeDouble(DISPLAY_DURATION)
        var styles: JSONObject? = jsonObject.safeJSONObject(STYLES)
        useHeightMargin = !(styles?.safeBool(REMOVE_HEIGHT_MARGIN) ?: false)
        useWidthMargin = !(styles?.safeBool(REMOVE_WIDTH_MARGIN) ?: false)
        isFullBleed = !useHeightMargin
    }

    companion object {
        const val HTML = "html"
        const val STYLES = "styles"
        const val DISPLAY_DURATION = "display_duration"
        const val REMOVE_HEIGHT_MARGIN = "remove_height_margin"
        const val REMOVE_WIDTH_MARGIN = "remove_width_margin"
    }
}
