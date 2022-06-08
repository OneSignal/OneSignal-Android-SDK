package com.onesignal.onesignal.internal.iam

import org.json.JSONException
import org.json.JSONObject

/**
 * An enumeration of the possible places action URL's can be loaded,
 * such as an in-app webview
 */
enum class InAppMessageActionUrlTypeExtension(private val text: String) {
    // Opens in an in-app webview
    IN_APP_WEBVIEW("webview"),  // Moves app to background and opens URL in browser
    BROWSER("browser"),  // Loads the URL on the in-app message webview itself
    REPLACE_CONTENT("replacement");

    override fun toString(): String {
        return text
    }

    fun toJSONObject(): JSONObject {
        val mainObj = JSONObject()
        try {
            mainObj.put("url_type", text)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return mainObj
    }

    companion object {
        fun fromString(text: String?): InAppMessageActionUrlTypeExtension? {
            for (type in values()) {
                if (type.text.equals(text, ignoreCase = true)) return type
            }
            return null
        }
    }
}