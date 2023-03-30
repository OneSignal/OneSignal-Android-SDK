package com.onesignal.session.internal.outcomes.impl

import com.onesignal.session.internal.influence.InfluenceType
import com.onesignal.session.internal.outcomes.IOutcomeEvent
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class OutcomeEvent(
    override val session: InfluenceType,
    override val notificationIds: JSONArray?,
    override val name: String,
    override val timestamp: Long,
    override val weight: Float,
) : IOutcomeEvent {
    @Throws(JSONException::class)
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put(SESSION, session)
        json.put(NOTIFICATION_IDS, notificationIds)
        json.put(OUTCOME_ID, name)
        json.put(TIMESTAMP, timestamp)
        json.put(WEIGHT, weight)
        return json
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || this.javaClass != o.javaClass) return false
        val event = o as OutcomeEvent
        return session == event.session && notificationIds == event.notificationIds && name == event.name && timestamp == event.timestamp && weight == event.weight
    }

    override fun hashCode(): Int {
        val a = arrayOf(session, notificationIds, name, timestamp, weight)
        var result = 1
        for (element in a) result = 31 * result + (element?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "OutcomeEvent{" +
            "session=" + session +
            ", notificationIds=" + notificationIds +
            ", name='" + name + '\'' +
            ", timestamp=" + timestamp +
            ", weight=" + weight +
            '}'
    }

    companion object {
        private const val SESSION = "session"
        private const val NOTIFICATION_IDS = "notification_ids"
        private const val OUTCOME_ID = "id"
        private const val TIMESTAMP = "timestamp"
        private const val WEIGHT = "weight"

        /**
         * Creates an OutcomeEvent from an OSOutcomeEventParams in order to work on V1 from V2
         */
        fun fromOutcomeEventParamstoOutcomeEvent(outcomeEventParams: OutcomeEventParams): OutcomeEvent {
            var influenceType = InfluenceType.UNATTRIBUTED
            var notificationId: JSONArray? = null
            if (outcomeEventParams.outcomeSource != null) {
                val source = outcomeEventParams.outcomeSource
                if (source.directBody != null && source.directBody!!.notificationIds != null && source.directBody!!.notificationIds!!.length() > 0) {
                    influenceType = InfluenceType.DIRECT
                    notificationId = source.directBody!!.notificationIds
                } else if (source.indirectBody != null && source.indirectBody!!.notificationIds != null && source.indirectBody!!.notificationIds!!.length() > 0) {
                    influenceType = InfluenceType.INDIRECT
                    notificationId = source.indirectBody!!.notificationIds
                }
            }
            return OutcomeEvent(
                influenceType,
                notificationId,
                outcomeEventParams.outcomeId,
                outcomeEventParams.timestamp,
                outcomeEventParams.weight,
            )
        }
    }
}
