package com.onesignal.onesignal.iam.internal

import com.onesignal.onesignal.iam.internal.display.impl.WebViewManager
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
        contentHtml = jsonObject.optString(HTML)
        displayDuration = jsonObject.optDouble(DISPLAY_DURATION)
        var styles: JSONObject? = jsonObject.optJSONObject(STYLES)
        useHeightMargin = !(styles?.optBoolean(REMOVE_HEIGHT_MARGIN, false) ?: false)
        useWidthMargin = !(styles?.optBoolean(REMOVE_WIDTH_MARGIN, false) ?: false)
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
