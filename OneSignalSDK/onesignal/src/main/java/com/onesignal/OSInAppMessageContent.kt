package com.onesignal

import org.json.JSONObject

const val HTML = "html"
const val STYLES = "styles"
const val DISPLAY_DURATION = "display_duration"
const val TOP_MARGIN = "top_margin"
const val BOTTOM_MARGIN = "bottom_margin"
const val LEFT_MARGIN = "left_margin"
const val RIGHT_MARGIN = "right_margin"

internal open class OSInAppMessageContent constructor(jsonObject: JSONObject) {
    var contentHtml: String? = null
    var topMargin: Int? = 1
    var bottomMargin: Int? = 1
    var leftMargin: Int? = 1
    var rightMargin: Int? = 1
    // The following properties are populated from Javascript events
    var displayLocation: WebViewManager.Position? = null
    var dismissDuration: Double? = null
    var pageHeight: Int = 0

    init {
        contentHtml = jsonObject.optString(HTML)
        displayDuration = jsonObject.optDouble(DISPLAY_DURATION)
        var styles: JSONObject? = jsonObject.optJSONObject(STYLES)
        topMargin = styles?.optInt(TOP_MARGIN, 1)
        bottomMargin = styles?.optInt(BOTTOM_MARGIN, 1)
        leftMargin = styles?.optInt(LEFT_MARGIN, 1)
        rightMargin = styles?.optInt(RIGHT_MARGIN,1)
    }
}