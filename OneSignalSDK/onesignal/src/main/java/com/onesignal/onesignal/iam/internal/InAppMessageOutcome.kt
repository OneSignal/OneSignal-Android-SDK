package com.onesignal.onesignal.iam.internal

import org.json.JSONException
import org.json.JSONObject

internal class InAppMessageOutcome(json: JSONObject) {
    /**
     * Outcome key for action
     */
    var name: String
    var weight: Float
    var isUnique: Boolean

    init {
        name = json.getString(OUTCOME_NAME)
        weight = if (json.has(OUTCOME_WEIGHT)) json.getDouble(OUTCOME_WEIGHT).toFloat() else 0F
        isUnique = json.has(OUTCOME_UNIQUE) && json.getBoolean(OUTCOME_UNIQUE)
    }

    fun toJSONObject(): JSONObject {
        val mainObj = JSONObject()
        try {
            mainObj.put(OUTCOME_NAME, name)
            mainObj.put(OUTCOME_WEIGHT, weight.toDouble())
            mainObj.put(OUTCOME_UNIQUE, isUnique)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return mainObj
    }

    override fun toString(): String {
        return "OSInAppMessageOutcome{" +
                "name='" + name + '\'' +
                ", weight=" + weight +
                ", unique=" + isUnique +
                '}'
    }

    companion object {
        private const val OUTCOME_NAME = "name"
        private const val OUTCOME_WEIGHT = "weight"
        private const val OUTCOME_UNIQUE = "unique"
    }
}