package com.onesignal.core.internal.influence.impl

import com.onesignal.core.internal.application.AppEntryAction
import com.onesignal.core.internal.influence.IChannelTracker
import com.onesignal.core.internal.influence.IInfluenceManager
import com.onesignal.core.internal.influence.Influence
import com.onesignal.core.internal.influence.InfluenceChannel
import com.onesignal.core.internal.params.IParamsService
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.time.ITime
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class InfluenceManager(
    preferences: IPreferencesService,
    timeProvider: ITime
) : IInfluenceManager {
    private val trackers = ConcurrentHashMap<String, ChannelTracker>()
    private val dataRepository: InfluenceDataRepository = InfluenceDataRepository(preferences)

    override val influences: List<Influence>
        get() = trackers.values.map { it.currentSessionInfluence }

    override val sessionInfluences: List<Influence>
        get() = trackers.values.filter { it.idTag != InfluenceConstants.IAM_TAG }.map { it.currentSessionInfluence }

    override val iAMChannelTracker: IChannelTracker
        get() = trackers[InfluenceConstants.IAM_TAG]!!

    override val notificationChannelTracker: IChannelTracker
        get() = trackers[InfluenceConstants.NOTIFICATION_TAG]!!

    override val channels: List<IChannelTracker>
        get() {
            val channels: MutableList<IChannelTracker> = mutableListOf()
            notificationChannelTracker.let { channels.add(it) }
            iAMChannelTracker.let { channels.add(it) }
            return channels
        }

    init {
        trackers[InfluenceConstants.IAM_TAG] = InAppMessageTracker(dataRepository, timeProvider)
        trackers[InfluenceConstants.NOTIFICATION_TAG] = NotificationTracker(dataRepository, timeProvider)
    }

    override fun initFromCache() {
        trackers.values.forEach {
            it.initInfluencedTypeFromCache()
        }
    }

    override fun saveInfluenceParams(influenceParams: IParamsService.InfluenceParams) {
        dataRepository.saveInfluenceParams(influenceParams)
    }

    override fun addSessionData(jsonObject: JSONObject, influences: List<Influence>) {
        influences.forEach {
            when (it.influenceChannel) {
                InfluenceChannel.NOTIFICATION -> trackers[InfluenceConstants.NOTIFICATION_TAG]!!.addSessionData(jsonObject, it)
                InfluenceChannel.IAM -> {
                    // IAM doesn't influence session
                }
            }
        }
    }

    override fun getChannelByEntryAction(entryAction: AppEntryAction): IChannelTracker? {
        return if (entryAction.isNotificationClick) notificationChannelTracker else null
    }

    override fun getChannelsToResetByEntryAction(entryAction: AppEntryAction): List<IChannelTracker> {
        val channels: MutableList<IChannelTracker> = ArrayList()
        // Avoid reset session if application is closed
        if (entryAction.isAppClose) return channels
        // Avoid reset session if app was focused due to a notification click (direct session recently set)
        val notificationChannel = if (entryAction.isAppOpen) notificationChannelTracker else null
        notificationChannel?.let {
            channels.add(it)
        }
        iAMChannelTracker.let {
            channels.add(it)
        }
        return channels
    }
}
