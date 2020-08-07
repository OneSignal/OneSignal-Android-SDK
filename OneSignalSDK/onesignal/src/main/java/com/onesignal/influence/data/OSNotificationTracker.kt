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

internal class OSNotificationTracker(dataRepository: OSInfluenceDataRepository, logger: OSLogger, timeProvider: OSTime) : OSChannelTracker(dataRepository, logger, timeProvider) {
    override fun getLastChannelObjectsReceivedByNewId(id: String?): JSONArray {
        return try {
            lastChannelObjects
        } catch (exception: JSONException) {
            logger.error("Generating Notification tracker getLastChannelObjects JSONObject ", exception)
            JSONArray()
        }
    }

    @get:Throws(JSONException::class)
    override val lastChannelObjects: JSONArray
        get() = dataRepository.lastNotificationsReceivedData
    override val idTag: String
        get() = OSInfluenceConstants.NOTIFICATION_ID_TAG

    override val channelType: OSInfluenceChannel
        get() = OSInfluenceChannel.NOTIFICATION

    override val channelLimit: Int
        get() = dataRepository.notificationLimit

    override val indirectAttributionWindow: Int
        get() = dataRepository.notificationIndirectAttributionWindow

    override fun saveChannelObjects(channelObjects: JSONArray) {
        dataRepository.saveNotifications(channelObjects)
    }

    override fun initInfluencedTypeFromCache() {
        influenceType = dataRepository.notificationCachedInfluenceType.also {
            if (it.isIndirect())
                indirectIds = lastReceivedIds
            else if (it.isDirect())
                directId = dataRepository.cachedNotificationOpenId
        }
        logger.debug("OneSignal NotificationTracker initInfluencedTypeFromCache: $this")
    }

    override fun addSessionData(jsonObject: JSONObject, influence: OSInfluence) {
        if (influence.influenceType.isAttributed()) try {
            jsonObject.put(OSInfluenceConstants.DIRECT_TAG, influence.influenceType.isDirect())
            jsonObject.put(OSInfluenceConstants.NOTIFICATIONS_IDS, influence.ids)
        } catch (exception: JSONException) {
            logger.error("Generating notification tracker addSessionData JSONObject ", exception)
        }
    }

    override fun cacheState() {
        dataRepository.cacheNotificationInfluenceType(influenceType ?: OSInfluenceType.UNATTRIBUTED)
        dataRepository.cacheNotificationOpenId(directId)
    }
}