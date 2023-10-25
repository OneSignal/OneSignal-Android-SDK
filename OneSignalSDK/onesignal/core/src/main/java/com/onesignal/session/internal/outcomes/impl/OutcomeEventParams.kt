package com.onesignal.session.internal.outcomes.impl

import org.json.JSONException
import org.json.JSONObject

internal class OutcomeEventParams constructor(
    val outcomeId: String,
    // This field is optional
    val outcomeSource: OutcomeSource?,
    // This field is optional
    var weight: Float,
    // This field is optional
    var sessionTime: Long,
    // This should start out as zero
    var timestamp: Long,
) {
    @Throws(JSONException::class)
    fun toJSONObject(): JSONObject {
        val json =
            JSONObject()
                .put(OutcomeConstants.OUTCOME_ID, outcomeId)
        outcomeSource?.let {
            json.put(OutcomeConstants.OUTCOME_SOURCES, it.toJSONObject())
        }
        if (weight > 0) json.put(OutcomeConstants.WEIGHT, weight)
        if (timestamp > 0) json.put(OutcomeConstants.TIMESTAMP, timestamp)
        if (sessionTime > 0) json.put(OutcomeConstants.SESSION_TIME, sessionTime)
        return json
    }

    fun isUnattributed() = outcomeSource == null || outcomeSource.directBody == null && outcomeSource.indirectBody == null

    override fun toString(): String {
        return "OutcomeEventParams{" +
            "outcomeId='" + outcomeId + '\'' +
            ", outcomeSource=" + outcomeSource +
            ", weight=" + weight +
            ", timestamp=" + timestamp +
            ", sessionTime=" + sessionTime +
            '}'
    }
}
