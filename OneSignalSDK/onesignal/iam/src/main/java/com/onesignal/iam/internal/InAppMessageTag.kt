package com.onesignal.iam.internal

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class InAppMessageTag(json: JSONObject) {
    var tagsToAdd: JSONObject?
    var tagsToRemove: JSONArray?
    fun toJSONObject(): JSONObject {
        val mainObj = JSONObject()
        try {
            if (tagsToAdd != null) mainObj.put(ADD_TAGS, tagsToAdd)
            if (tagsToRemove != null) mainObj.put(REMOVE_TAGS, tagsToRemove)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return mainObj
    }

    override fun toString(): String {
        return "OSInAppMessageTag{" +
            "adds=" + tagsToAdd +
            ", removes=" + tagsToRemove +
            '}'
    }

    companion object {
        // TODO when backend is ready check if key match
        private const val ADD_TAGS = "adds"

        // TODO when backend is ready check if key match
        private const val REMOVE_TAGS = "removes"
    }

    init {
        tagsToAdd = if (json.has(ADD_TAGS)) json.getJSONObject(ADD_TAGS) else null
        tagsToRemove = if (json.has(REMOVE_TAGS)) json.getJSONArray(REMOVE_TAGS) else null
    }
}
