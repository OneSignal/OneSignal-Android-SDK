package com.onesignal.core.internal.influence.impl

import com.onesignal.core.internal.influence.Influence
import com.onesignal.core.internal.influence.InfluenceChannel
import com.onesignal.core.internal.influence.InfluenceType
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.time.ITime
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class InAppMessageTracker(dataRepository: InfluenceDataRepository, timeProvider: ITime) : ChannelTracker(dataRepository, timeProvider) {
    override val idTag: String
        get() = InfluenceConstants.IAM_ID_TAG

    override val channelType: InfluenceChannel
        get() = InfluenceChannel.IAM

    override fun getLastChannelObjectsReceivedByNewId(id: String?): JSONArray {
        var lastChannelObjectReceived: JSONArray
        lastChannelObjectReceived = try {
            lastChannelObjects
        } catch (exception: JSONException) {
            Logging.error("Generating IAM tracker getLastChannelObjects JSONObject ", exception)
            return JSONArray()
        }
        // For IAM we handle redisplay, we need to remove duplicates for new influence Id
        // If min sdk is greater than KITKAT we can refactor this logic to removeObject from JSONArray
        try {
            val auxLastChannelObjectReceived = JSONArray()
            for (i in 0 until lastChannelObjectReceived.length()) {
                val objectId = lastChannelObjectReceived.getJSONObject(i).getString(idTag)
                if (id != objectId) {
                    auxLastChannelObjectReceived.put(lastChannelObjectReceived.getJSONObject(i))
                }
            }
            lastChannelObjectReceived = auxLastChannelObjectReceived
        } catch (exception: JSONException) {
            Logging.error("Generating tracker lastChannelObjectReceived get JSONObject ", exception)
        }
        return lastChannelObjectReceived
    }

    @get:Throws(JSONException::class)
    override val lastChannelObjects: JSONArray
        get() = dataRepository.lastIAMsReceivedData

    override val channelLimit: Int
        get() = dataRepository.iamLimit

    override val indirectAttributionWindow: Int
        get() = dataRepository.iamIndirectAttributionWindow

    override fun saveChannelObjects(channelObjects: JSONArray) {
        dataRepository.saveIAMs(channelObjects)
    }

    override fun initInfluencedTypeFromCache() {
        influenceType = dataRepository.iamCachedInfluenceType.also {
            if (it.isIndirect()) indirectIds = lastReceivedIds
        }
        Logging.debug("OneSignal InAppMessageTracker initInfluencedTypeFromCache: $this")
    }

    override fun addSessionData(jsonObject: JSONObject, influence: Influence) {
        // In app message don't influence the session
    }

    override fun cacheState() {
        // We only need to cache INDIRECT and UNATTRIBUTED influence types
        // DIRECT is downgrade to INDIRECT to avoid inconsistency state
        // where the app might be close before dismissing current displayed IAM
        val influenceTypeToCache = influenceType ?: InfluenceType.UNATTRIBUTED
        dataRepository.cacheIAMInfluenceType(if (influenceTypeToCache === InfluenceType.DIRECT) InfluenceType.INDIRECT else influenceTypeToCache)
    }
}
