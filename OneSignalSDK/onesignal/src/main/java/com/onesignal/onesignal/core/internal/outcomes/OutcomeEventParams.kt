package com.onesignal.onesignal.core.internal.outcomes

import org.json.JSONException
import org.json.JSONObject

class OutcomeEventParams constructor(
    val outcomeId: String,
    val outcomeSource: OutcomeSource?, // This field is optional
    var weight: Float, // This field is optional.
    var timestamp: Long = 0
) {
    @Throws(JSONException::class)
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
            .put(OutcomeConstants.OUTCOME_ID, outcomeId)
        outcomeSource?.let {
            json.put(OutcomeConstants.OUTCOME_SOURCES, it.toJSONObject())
        }
        if (weight > 0) json.put(OutcomeConstants.WEIGHT, weight)
        if (timestamp > 0) json.put(OutcomeConstants.TIMESTAMP, timestamp)
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
