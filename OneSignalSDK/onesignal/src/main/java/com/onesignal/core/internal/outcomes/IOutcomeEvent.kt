package com.onesignal.core.internal.outcomes

import com.onesignal.core.internal.influence.InfluenceType
import org.json.JSONArray

internal interface IOutcomeEvent {
    val session: InfluenceType
    val notificationIds: JSONArray?
    val name: String
    val timestamp: Long
    val weight: Float
}
