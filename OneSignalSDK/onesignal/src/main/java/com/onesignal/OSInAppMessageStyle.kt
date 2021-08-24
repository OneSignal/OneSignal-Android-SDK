package com.onesignal

import org.json.JSONObject

const val HTML = "html"
const val STYLE = "style"
const val TOP_MARGIN = "top_margin"
const val BOTTOM_MARGIN = "bottom_margin"
const val LEFT_MARGIN = "left_margin"
const val RIGHT_MARGIN = "right_margin"

class OSInAppMessageContent constructor(jsonObject: JSONObject) {
    var contentHtml: String? = null
    var topMargin: Int = 1
    var bottomMargin: Int = 1
    var leftMargin: Int = 1
    var rightMargin: Int = 1

    init {
        contentHtml = jsonObject.optString(HTML)
        var style: JSONObject = jsonObject.optJSONObject(STYLE)
        topMargin = style.optInt(TOP_MARGIN, 1)
        bottomMargin = style.optInt(BOTTOM_MARGIN, 1)
        leftMargin = style.optInt(LEFT_MARGIN, 1)
        rightMargin = style.optInt(RIGHT_MARGIN, 1)
    }
}