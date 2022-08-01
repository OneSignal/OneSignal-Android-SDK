package com.onesignal.onesignal.core.internal.common

import android.os.Bundle
import com.onesignal.onesignal.core.internal.logging.Logging
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.regex.Matcher
import java.util.regex.Pattern

object JSONUtils {
    const val EXTERNAL_USER_ID = "external_user_id"

    /**
     * Wrap the provided [JSONObject] as a [JSONArray] with
     * one entry (the object).
     *
     * @param jsonObject The object that is to be wrapped within an array.
     *
     * @return A [JSONArray] containing one entry, the [jsonObject] provided.
     */
    fun wrapInJsonArray(jsonObject: JSONObject?): JSONArray {
        return JSONArray().put(jsonObject)
    }

    fun bundleAsJSONObject(bundle: Bundle): JSONObject {
        val json = JSONObject()
        val keys = bundle.keySet()
        for (key in keys) {
            try {
                json.put(key, bundle[key])
            } catch (e: JSONException) {
                Logging.error("bundleAsJSONObject error for key: $key", e)
            }
        }
        return json
    }

    fun jsonStringToBundle(data: String): Bundle? {
        return try {
            val jsonObject = JSONObject(data)
            val bundle = Bundle()
            val iterator: Iterator<*> = jsonObject.keys()
            while (iterator.hasNext()) {
                val key = iterator.next() as String
                val value = jsonObject.getString(key)
                bundle.putString(key, value)
            }
            bundle
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }

    // Creates a new Set<String> from a Set String by converting and iterating a JSONArray
    fun newStringSetFromJSONArray(jsonArray: JSONArray): Set<String> {
        val stringSet: MutableSet<String> = mutableSetOf()
        for (i in 0 until jsonArray.length()) {
            stringSet.add(jsonArray.getString(i))
        }
        return stringSet
    }

    /**
     * Returns the JSONObject as a String with the external user ID unescaped.
     * Needed b/c the default JSONObject.toString() escapes (/) with (\/), which customers may not want.
     */
    fun toUnescapedEUIDString(json: JSONObject): String {
        var strJsonBody = json.toString()
        if (json.has(EXTERNAL_USER_ID)) {
            // find the value of the external user ID
            val eidPattern = Pattern.compile("(?<=\"external_user_id\":\").*?(?=\")")
            val eidMatcher = eidPattern.matcher(strJsonBody)
            if (eidMatcher.find()) {
                val matched = eidMatcher.group(0)
                if (matched != null) {
                    var unescapedEID: String? = matched.replace("\\/", "/")
                    // backslashes (\) and dollar signs ($) in the replacement string will be treated literally
                    unescapedEID = Matcher.quoteReplacement(unescapedEID)
                    strJsonBody = eidMatcher.replaceAll(unescapedEID)
                }
            }
        }
        return strJsonBody
    }

}