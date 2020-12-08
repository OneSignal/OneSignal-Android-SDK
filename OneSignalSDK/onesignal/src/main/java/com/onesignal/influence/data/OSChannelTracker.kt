package com.onesignal.influence.data

import com.onesignal.OSLogger
import com.onesignal.OSTime
import com.onesignal.influence.OSInfluenceConstants
import com.onesignal.influence.domain.OSInfluence
import com.onesignal.influence.domain.OSInfluenceChannel
import com.onesignal.influence.domain.OSInfluenceType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

abstract class OSChannelTracker internal constructor(protected var dataRepository: OSInfluenceDataRepository, var logger: OSLogger, private var timeProvider: OSTime) {
    var influenceType: OSInfluenceType? = null
    var indirectIds: JSONArray? = null
    var directId: String? = null

    abstract val idTag: String
    abstract val channelType: OSInfluenceChannel

    @get:Throws(JSONException::class)
    abstract val lastChannelObjects: JSONArray
    abstract val channelLimit: Int
    abstract val indirectAttributionWindow: Int
    abstract fun getLastChannelObjectsReceivedByNewId(id: String?): JSONArray
    abstract fun saveChannelObjects(channelObjects: JSONArray)
    abstract fun initInfluencedTypeFromCache()
    abstract fun cacheState()
    abstract fun addSessionData(jsonObject: JSONObject, influence: OSInfluence)

    private val isDirectSessionEnabled: Boolean
        get() = dataRepository.isDirectInfluenceEnabled

    private val isIndirectSessionEnabled: Boolean
        get() = dataRepository.isIndirectInfluenceEnabled

    private val isUnattributedSessionEnabled: Boolean
        get() = dataRepository.isUnattributedInfluenceEnabled

    /**
     * Get the current session based on state + if outcomes features are enabled.
     */
    val currentSessionInfluence: OSInfluence
        get() {
            val sessionInfluence = OSInfluence(channelType, OSInfluenceType.DISABLED, null)
            // Channel weren't init yet because application is starting
            if (influenceType == null) initInfluencedTypeFromCache()

            val currentInfluenceType = influenceType ?: OSInfluenceType.DISABLED

            if (currentInfluenceType.isDirect()) {
                if (isDirectSessionEnabled) {
                    sessionInfluence.apply {
                        ids = JSONArray().put(this@OSChannelTracker.directId)
                        influenceType = OSInfluenceType.DIRECT
                    }
                }
            } else if (currentInfluenceType.isIndirect()) {
                if (isIndirectSessionEnabled) {
                    sessionInfluence.apply {
                        ids = this@OSChannelTracker.indirectIds
                        influenceType = OSInfluenceType.INDIRECT
                    }
                }
            } else if (isUnattributedSessionEnabled) {
                sessionInfluence.apply {
                    influenceType = OSInfluenceType.UNATTRIBUTED
                }
            }

            return sessionInfluence
        }

    /**
     * Get all received ids that may influence actions
     * @return ids that happen between attribution window
     */
    val lastReceivedIds: JSONArray
        get() {
            val ids = JSONArray()
            try {
                val lastChannelObjectReceived = lastChannelObjects
                logger.debug("OneSignal ChannelTracker getLastReceivedIds lastChannelObjectReceived: $lastChannelObjectReceived")
                val attributionWindow = indirectAttributionWindow * 60 * 1000L
                val currentTime = timeProvider.currentTimeMillis
                for (i in 0 until lastChannelObjectReceived.length()) {
                    val jsonObject = lastChannelObjectReceived.getJSONObject(i)
                    val time = jsonObject.getLong(OSInfluenceConstants.TIME)
                    val difference = currentTime - time
                    if (difference <= attributionWindow) {
                        val id = jsonObject.getString(idTag)
                        ids.put(id)
                    }
                }
            } catch (exception: JSONException) {
                logger.error("Generating tracker getLastReceivedIds JSONObject ", exception)
            }
            return ids
        }

    fun resetAndInitInfluence() {
        directId = null
        indirectIds = lastReceivedIds
        influenceType = if (indirectIds?.length() ?: 0 > 0) OSInfluenceType.INDIRECT else OSInfluenceType.UNATTRIBUTED
        cacheState()
        logger.debug("OneSignal OSChannelTracker resetAndInitInfluence: $idTag finish with influenceType: $influenceType")
    }

    /**
     * Save state of last ids received
     */
    fun saveLastId(id: String?) {
        logger.debug("OneSignal OSChannelTracker for: $idTag saveLastId: $id")
        if (id == null || id.isEmpty()) return

        val lastChannelObjectsReceived = getLastChannelObjectsReceivedByNewId(id)
        logger.debug("OneSignal OSChannelTracker for: $idTag saveLastId with lastChannelObjectsReceived: $lastChannelObjectsReceived")

        try {
            timeProvider.run {
                JSONObject()
                        .put(idTag, id)
                        .put(OSInfluenceConstants.TIME, currentTimeMillis)
            }.also { newInfluenceId ->
                lastChannelObjectsReceived.put(newInfluenceId)
            }
        } catch (exception: JSONException) {
            logger.error("Generating tracker newInfluenceId JSONObject ", exception)
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
                    logger.error("Generating tracker lastChannelObjectsReceived get JSONObject ", exception)
                }
            }
        }
        logger.debug("OneSignal OSChannelTracker for: $idTag with channelObjectToSave: $channelObjectToSave")
        saveChannelObjects(channelObjectToSave)
    }

    override fun toString(): String {
        return "OSChannelTracker{" +
                "tag=" + idTag +
                ", influenceType=" + influenceType +
                ", indirectIds=" + indirectIds +
                ", directId=" + directId +
                '}'
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val tracker = other as OSChannelTracker
        return influenceType === tracker.influenceType && tracker.idTag == idTag
    }

    override fun hashCode(): Int {
        var result = influenceType.hashCode()
        result = 31 * result + idTag.hashCode()
        return result
    }
}