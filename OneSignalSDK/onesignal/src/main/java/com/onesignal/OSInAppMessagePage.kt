package com.onesignal


import org.json.JSONException
import org.json.JSONObject

const val PAGE_ID = "page_id"
const val PAGE_INDEX = "page_index"

class OSInAppMessagePage constructor(jsonObject: JSONObject) {
    var pageId: String? = null
    var pageIndex: String? = null

    init {
        pageId = jsonObject.optString(PAGE_ID, null)
        pageIndex = jsonObject.optString(PAGE_INDEX, null)
    }

    fun toJSONObject(): JSONObject? {
        val mainObj = JSONObject()
        try {
            mainObj.put(PAGE_ID, pageId)
            mainObj.put(PAGE_INDEX, pageIndex)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return mainObj
    }
}