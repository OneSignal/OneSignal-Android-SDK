package com.onesignal.inAppMessages.internal

import org.json.JSONException
import org.json.JSONObject

internal open class InAppMessagePage constructor(jsonObject: JSONObject) {
    var pageId: String? = null
    var pageIndex: String? = null

    init {
        pageId = jsonObject.optString(PAGE_ID, null)
        pageIndex = jsonObject.optString(PAGE_INDEX, null)
    }

    fun toJSONObject(): JSONObject {
        val mainObj = JSONObject()
        try {
            mainObj.put(PAGE_ID, pageId)
            mainObj.put(PAGE_INDEX, pageIndex)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return mainObj
    }

    companion object {
        const val PAGE_ID = "pageId"
        const val PAGE_INDEX = "pageIndex"
    }
}
