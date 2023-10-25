package com.onesignal.session.internal.influence.impl

import com.onesignal.session.internal.influence.Influence
import com.onesignal.session.internal.influence.InfluenceChannel
import com.onesignal.session.internal.influence.InfluenceType
import org.json.JSONArray

internal interface IChannelTracker {
    var influenceType: InfluenceType?
    var indirectIds: JSONArray?
    var directId: String?
    val idTag: String
    val channelType: InfluenceChannel

    /**
     * Get all received ids that may influence actions
     * @return ids that happen between attribution window
     */
    val lastReceivedIds: JSONArray
    val currentSessionInfluence: Influence

    fun cacheState()

    fun resetAndInitInfluence()

    /**
     * Save state of last ids received
     */
    fun saveLastId(id: String?)
}
