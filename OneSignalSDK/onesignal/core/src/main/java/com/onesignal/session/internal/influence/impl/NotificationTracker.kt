package com.onesignal.session.internal.influence.impl

import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.influence.InfluenceChannel
import com.onesignal.session.internal.influence.InfluenceType
import org.json.JSONArray
import org.json.JSONException

internal class NotificationTracker(dataRepository: InfluenceDataRepository, timeProvider: ITime) : ChannelTracker(
    dataRepository,
    timeProvider,
) {
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
        influenceType =
            dataRepository.notificationCachedInfluenceType.also {
                if (it.isIndirect()) {
                    indirectIds = lastReceivedIds
                } else if (it.isDirect()) {
                    directId = dataRepository.cachedNotificationOpenId
                }
            }
        Logging.debug("NotificationTracker.initInfluencedTypeFromCache: $this")
    }

    override fun cacheState() {
        dataRepository.cacheNotificationInfluenceType(influenceType ?: InfluenceType.UNATTRIBUTED)
        dataRepository.cacheNotificationOpenId(directId)
    }
}
