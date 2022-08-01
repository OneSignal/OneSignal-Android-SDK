package com.onesignal.onesignal.core.internal.influence

import org.json.JSONArray

interface IChannelTracker {
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

    fun cacheState()
    fun resetAndInitInfluence()

    /**
     * Save state of last ids received
     */
    fun saveLastId(id: String?)
}