package com.onesignal.session.internal.outcomes

import com.onesignal.session.internal.influence.InfluenceType
import org.json.JSONArray

interface IOutcomeEvent {
    val session: InfluenceType
    val notificationIds: JSONArray?
    val name: String
    val timestamp: Long
    val sessionTime: Long // in seconds
    val weight: Float
}
