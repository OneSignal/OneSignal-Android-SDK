package com.onesignal.core.internal.influence.impl

import com.onesignal.core.internal.influence.Influence
import com.onesignal.core.internal.influence.InfluenceChannel
import com.onesignal.core.internal.influence.InfluenceType
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
