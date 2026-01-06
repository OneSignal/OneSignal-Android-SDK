package com.onesignal.session.internal.influence.impl

import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.influence.Influence
import com.onesignal.session.internal.influence.InfluenceType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal abstract class ChannelTracker internal constructor(protected var dataRepository: InfluenceDataRepository, private var timeProvider: ITime) :
    IChannelTracker {
    override var influenceType: InfluenceType? = null
    override var indirectIds: JSONArray? = null
    override var directId: String? = null

    @get:Throws(JSONException::class)
    abstract val lastChannelObjects: JSONArray
    abstract val channelLimit: Int
    abstract val indirectAttributionWindow: Int

    abstract fun getLastChannelObjectsReceivedByNewId(id: String?): JSONArray

    abstract fun saveChannelObjects(channelObjects: JSONArray)

    abstract fun initInfluencedTypeFromCache()

    private val isDirectSessionEnabled: Boolean
        get() = dataRepository.isDirectInfluenceEnabled

    private val isIndirectSessionEnabled: Boolean
        get() = dataRepository.isIndirectInfluenceEnabled

    private val isUnattributedSessionEnabled: Boolean
        get() = dataRepository.isUnattributedInfluenceEnabled

    /**
     * Get the current session based on state + if outcomes features are enabled.
     */
    override val currentSessionInfluence: Influence
        get() {
            val sessionInfluence = Influence(channelType, InfluenceType.DISABLED, null)
            // Channel weren't init yet because application is starting
            if (influenceType == null) initInfluencedTypeFromCache()

            val currentInfluenceType = influenceType ?: InfluenceType.DISABLED

            if (currentInfluenceType.isDirect()) {
                if (isDirectSessionEnabled) {
                    sessionInfluence.apply {
                        ids = JSONArray().put(this@ChannelTracker.directId)
                        influenceType = InfluenceType.DIRECT
                    }
                }
            } else if (currentInfluenceType.isIndirect()) {
                if (isIndirectSessionEnabled) {
                    sessionInfluence.apply {
                        ids = this@ChannelTracker.indirectIds
                        influenceType = InfluenceType.INDIRECT
                    }
                }
            } else if (isUnattributedSessionEnabled) {
                sessionInfluence.apply {
                    influenceType = InfluenceType.UNATTRIBUTED
                }
            }

            return sessionInfluence
        }

    /**
     * Get all received ids that may influence actions
     * @return ids that happen between attribution window
     */
    override val lastReceivedIds: JSONArray
        get() {
            val ids = JSONArray()
            try {
                val lastChannelObjectReceived = lastChannelObjects
                Logging.debug("ChannelTracker.getLastReceivedIds: lastChannelObjectReceived: $lastChannelObjectReceived")
                val attributionWindow = indirectAttributionWindow * 60 * 1000L
                val currentTime = timeProvider.currentTimeMillis
                for (i in 0 until lastChannelObjectReceived.length()) {
                    val jsonObject = lastChannelObjectReceived.getJSONObject(i)
                    val time = jsonObject.getLong(InfluenceConstants.TIME)
                    val difference = currentTime - time
                    if (difference <= attributionWindow) {
                        val id = jsonObject.getString(idTag)
                        ids.put(id)
                    }
                }
            } catch (exception: JSONException) {
                Logging.error("ChannelTracker.getLastReceivedIds: Generating tracker getLastReceivedIds JSONObject ", exception)
            }
            return ids
        }

    override fun resetAndInitInfluence() {
        directId = null
        indirectIds = lastReceivedIds
        influenceType = if (indirectIds?.length() ?: 0 > 0) InfluenceType.INDIRECT else InfluenceType.UNATTRIBUTED
        cacheState()
        Logging.debug("ChannelTracker.resetAndInitInfluence: $idTag finish with influenceType: $influenceType")
    }

    /**
     * Save state of last ids received
     */
    override fun saveLastId(id: String?) {
        Logging.debug("ChannelTracker.saveLastId(id: $id): idTag=$idTag")
        if (id == null || id.isEmpty()) return

        val lastChannelObjectsReceived = getLastChannelObjectsReceivedByNewId(id)
        Logging.debug("ChannelTracker.saveLastId: for $idTag saveLastId with lastChannelObjectsReceived: $lastChannelObjectsReceived")

        try {
            timeProvider.run {
                JSONObject()
                    .put(idTag, id)
                    .put(InfluenceConstants.TIME, currentTimeMillis)
            }.also { newInfluenceId ->
                lastChannelObjectsReceived.put(newInfluenceId)
            }
        } catch (exception: JSONException) {
            Logging.error("ChannelTracker.saveLastId: Generating tracker newInfluenceId JSONObject ", exception)
            // We don't have new data, stop logic
            return
        }

        var channelObjectToSave = lastChannelObjectsReceived
        // Only save the last ids without surpassing the limit
        // Always keep the max quantity of ids possible
        // If the attribution window increases, old ids might influence
        if (lastChannelObjectsReceived.length() > channelLimit) {
            val lengthDifference = lastChannelObjectsReceived.length() - channelLimit
            // If min sdk is greater than KITKAT we can refactor this logic to removeObject from JSONArray
            channelObjectToSave = JSONArray()
            for (i in lengthDifference until lastChannelObjectsReceived.length()) {
                try {
                    channelObjectToSave.put(lastChannelObjectsReceived[i])
                } catch (exception: JSONException) {
                    Logging.error("ChannelTracker.saveLastId: Generating tracker lastChannelObjectsReceived get JSONObject ", exception)
                }
            }
        }
        Logging.debug("ChannelTracker.saveLastId: for $idTag with channelObjectToSave: $channelObjectToSave")
        saveChannelObjects(channelObjectToSave)
    }

    override fun toString(): String {
        return "ChannelTracker{" +
            "tag=" + idTag +
            ", influenceType=" + influenceType +
            ", indirectIds=" + indirectIds +
            ", directId=" + directId +
            '}'
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val tracker = other as ChannelTracker
        return influenceType === tracker.influenceType && tracker.idTag == idTag
    }

    override fun hashCode(): Int {
        var result = influenceType.hashCode()
        result = 31 * result + idTag.hashCode()
        return result
    }
}
