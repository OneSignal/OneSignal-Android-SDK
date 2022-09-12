package com.onesignal.core.internal.influence.impl

import com.onesignal.core.internal.influence.Influence
import com.onesignal.core.internal.influence.InfluenceChannel
import com.onesignal.core.internal.influence.InfluenceType
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.time.ITime
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class NotificationTracker(dataRepository: InfluenceDataRepository, timeProvider: ITime) : ChannelTracker(dataRepository, timeProvider) {
    override fun getLastChannelObjectsReceivedByNewId(id: String?): JSONArray {
        return try {
            lastChannelObjects
        } catch (exception: JSONException) {
            Logging.error("Generating Notification tracker getLastChannelObjects JSONObject ", exception)
            JSONArray()
        }
    }

    @get:Throws(JSONException::class)
    override val lastChannelObjects: JSONArray
        get() = dataRepository.lastNotificationsReceivedData
    override val idTag: String
        get() = InfluenceConstants.NOTIFICATION_ID_TAG

    override val channelType: InfluenceChannel
        get() = InfluenceChannel.NOTIFICATION

    override val channelLimit: Int
        get() = dataRepository.notificationLimit

    override val indirectAttributionWindow: Int
        get() = dataRepository.notificationIndirectAttributionWindow

    override fun saveChannelObjects(channelObjects: JSONArray) {
        dataRepository.saveNotifications(channelObjects)
    }

    override fun initInfluencedTypeFromCache() {
        influenceType = dataRepository.notificationCachedInfluenceType.also {
            if (it.isIndirect()) {
                indirectIds = lastReceivedIds
            } else if (it.isDirect()) {
                directId = dataRepository.cachedNotificationOpenId
            }
        }
        Logging.debug("OneSignal NotificationTracker initInfluencedTypeFromCache: $this")
    }

    override fun addSessionData(jsonObject: JSONObject, influence: Influence) {
        if (influence.influenceType.isAttributed()) {
            try {
                jsonObject.put(InfluenceConstants.DIRECT_TAG, influence.influenceType.isDirect())
                jsonObject.put(InfluenceConstants.NOTIFICATIONS_IDS, influence.ids)
            } catch (exception: JSONException) {
                Logging.error("Generating notification tracker addSessionData JSONObject ", exception)
            }
        }
    }

    override fun cacheState() {
        dataRepository.cacheNotificationInfluenceType(influenceType ?: InfluenceType.UNATTRIBUTED)
        dataRepository.cacheNotificationOpenId(directId)
    }
}
