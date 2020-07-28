package com.onesignal.outcomes.domain

import com.onesignal.outcomes.OSOutcomeConstants
import org.json.JSONException
import org.json.JSONObject

class OSOutcomeEventParams constructor(val outcomeId: String,
                                       val outcomeSource: OSOutcomeSource?, // This field is optional
                                       var weight: Float, // This field is optional.
                                       var timestamp: Long = 0) {
    @Throws(JSONException::class)
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
                .put(OSOutcomeConstants.OUTCOME_ID, outcomeId)
        outcomeSource?.let {
            json.put(OSOutcomeConstants.OUTCOME_SOURCES, it.toJSONObject())
        }
        if (weight > 0) json.put(OSOutcomeConstants.WEIGHT, weight)
        if (timestamp > 0) json.put(OSOutcomeConstants.TIMESTAMP, timestamp)
        return json
    }

    fun isUnattributed() = outcomeSource == null || outcomeSource.directBody == null && outcomeSource.indirectBody == null

    override fun toString(): String {
        return "OSOutcomeEventParams{" +
                "outcomeId='" + outcomeId + '\'' +
                ", outcomeSource=" + outcomeSource +
                ", weight=" + weight +
                ", timestamp=" + timestamp +
                '}'
    }
}