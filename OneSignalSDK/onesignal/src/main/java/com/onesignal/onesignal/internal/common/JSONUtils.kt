package com.onesignal.onesignal.internal.common

import android.os.Bundle
import com.onesignal.onesignal.logging.Logging
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object JSONUtils {

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
}