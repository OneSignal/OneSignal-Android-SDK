package com.onesignal.core.internal.influence.impl

import com.onesignal.core.internal.application.AppEntryAction
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.common.JSONUtils
import com.onesignal.core.internal.influence.IInfluenceManager
import com.onesignal.core.internal.influence.Influence
import com.onesignal.core.internal.influence.InfluenceChannel
import com.onesignal.core.internal.influence.InfluenceType
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.session.ISessionLifecycleHandler
import com.onesignal.core.internal.session.ISessionService
import com.onesignal.core.internal.time.ITime
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal class InfluenceManager(
    private val _sessionService: ISessionService,
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
    preferences: IPreferencesService,
    timeProvider: ITime
) : IInfluenceManager, ISessionLifecycleHandler {
    private val trackers = ConcurrentHashMap<String, ChannelTracker>()
    private val dataRepository: InfluenceDataRepository = InfluenceDataRepository(preferences, _configModelStore)

    override val influences: List<Influence>
        get() = trackers.values.map { it.currentSessionInfluence }

    private val iAMChannelTracker: IChannelTracker
        get() = trackers[InfluenceConstants.IAM_TAG]!!

    private val notificationChannelTracker: IChannelTracker
        get() = trackers[InfluenceConstants.NOTIFICATION_TAG]!!

    private val channels: List<IChannelTracker>
        get() {
            val channels: MutableList<IChannelTracker> = mutableListOf()
            notificationChannelTracker.let { channels.add(it) }
            iAMChannelTracker.let { channels.add(it) }
            return channels
        }

    init {
        trackers[InfluenceConstants.IAM_TAG] = InAppMessageTracker(dataRepository, timeProvider)
        trackers[InfluenceConstants.NOTIFICATION_TAG] = NotificationTracker(dataRepository, timeProvider)

        _sessionService.subscribe(this)

        trackers.values.forEach {
            it.initInfluencedTypeFromCache()
        }
    }

    override fun sessionStarted() {
        restartSessionTrackersIfNeeded(_applicationService.entryState)
    }

    override fun sessionResumed() {
        attemptSessionUpgrade(_applicationService.entryState)
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

    private fun getChannelByEntryAction(entryAction: AppEntryAction): IChannelTracker? {
        return if (entryAction.isNotificationClick) notificationChannelTracker else null
    }

    private fun getChannelsToResetByEntryAction(entryAction: AppEntryAction): List<IChannelTracker> {
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

    override fun onNotificationReceived(notificationId: String) {
        Logging.debug("InfluenceManager.onNotificationReceived(notificationId: $notificationId)")

        if (notificationId.isEmpty()) {
            return
        }

        notificationChannelTracker.saveLastId(notificationId)
    }

    override fun onDirectInfluenceFromNotification(notificationId: String) {
        Logging.debug("InfluenceManager.onDirectInfluenceFromNotification(notificationId: $notificationId)")

        if (notificationId.isEmpty()) {
            return
        }

        attemptSessionUpgrade(AppEntryAction.NOTIFICATION_CLICK, notificationId)
    }

    override fun onInAppMessageDisplayed(messageId: String) {
        Logging.debug("InfluenceManager.onInAppMessageReceived(messageId: $messageId)")
        val inAppMessageTracker = iAMChannelTracker
        inAppMessageTracker.saveLastId(messageId)
        inAppMessageTracker.resetAndInitInfluence()
    }

    override fun onDirectInfluenceFromIAM(messageId: String) {
        Logging.debug("InfluenceManager.onDirectInfluenceFromIAM(messageId: $messageId)")

        // We don't care about ending the session duration because IAM doesn't influence a session
        setSessionTracker(iAMChannelTracker, InfluenceType.DIRECT, messageId, null)
    }

    override fun onInAppMessageDismissed() {
        Logging.debug("InfluenceManager.onInAppMessageDismissed()")
        val inAppMessageTracker = iAMChannelTracker
        inAppMessageTracker.resetAndInitInfluence()
    }

    private fun restartSessionTrackersIfNeeded(entryAction: AppEntryAction) {
        val channelTrackers: List<IChannelTracker> = getChannelsToResetByEntryAction(entryAction)
        val updatedInfluences: MutableList<Influence> = ArrayList()
        Logging.debug("InfluenceManager.restartSessionIfNeeded(entryAction: $entryAction):\n channelTrackers: $channelTrackers")

        for (channelTracker in channelTrackers) {
            val lastIds = channelTracker.lastReceivedIds
            Logging.debug("InfluenceManager.restartSessionIfNeeded: lastIds: $lastIds")
            val influence = channelTracker.currentSessionInfluence
            var updated: Boolean = if (lastIds.length() > 0) {
                setSessionTracker(
                    channelTracker,
                    InfluenceType.INDIRECT,
                    null,
                    lastIds
                )
            } else {
                setSessionTracker(channelTracker, InfluenceType.UNATTRIBUTED, null, null)
            }
            if (updated) updatedInfluences.add(influence)
        }
    }

    private fun setSessionTracker(channelTracker: IChannelTracker, influenceType: InfluenceType, directNotificationId: String?, indirectNotificationIds: JSONArray?): Boolean {
        if (!willChangeSessionTracker(channelTracker, influenceType, directNotificationId, indirectNotificationIds)) {
            return false
        }
        Logging.debug(
            """
            ChannelTracker changed: ${channelTracker.idTag}
            from:
            influenceType: ${channelTracker.influenceType}, directNotificationId: ${channelTracker.directId}, indirectNotificationIds: ${channelTracker.indirectIds}
            to:
            influenceType: $influenceType, directNotificationId: $directNotificationId, indirectNotificationIds: $indirectNotificationIds
            """.trimIndent()
        )

        channelTracker.influenceType = influenceType
        channelTracker.directId = directNotificationId
        channelTracker.indirectIds = indirectNotificationIds
        channelTracker.cacheState()
        Logging.debug("InfluenceManager.setSessionTracker: Trackers changed to: $channels")
        // Session changed
        return true
    }

    private fun willChangeSessionTracker(channelTracker: IChannelTracker, influenceType: InfluenceType, directNotificationId: String?, indirectNotificationIds: JSONArray?): Boolean {
        if (influenceType != channelTracker.influenceType) {
            return true
        }

        val channelInfluenceType = channelTracker.influenceType

        // Allow updating a direct session to a new direct when a new notification is clicked
        return if (channelInfluenceType?.isDirect() == true && channelTracker.directId != null &&
            channelTracker.directId != directNotificationId
        ) {
            true
        } else {
            channelInfluenceType?.isIndirect() == true && channelTracker.indirectIds != null && channelTracker.indirectIds!!.length() > 0 &&
                !JSONUtils.compareJSONArrays(channelTracker.indirectIds, indirectNotificationIds)
        }

        // Allow updating an indirect session to a new indirect when a new notification is received
    }

    private fun attemptSessionUpgrade(entryAction: AppEntryAction, directId: String? = null) {
        Logging.debug("InfluenceManager.attemptSessionUpgrade(entryAction: $entryAction, directId: $directId)")
        val channelTrackerByAction = getChannelByEntryAction(entryAction)
        val channelTrackersToReset = getChannelsToResetByEntryAction(entryAction)
        val influencesToEnd: MutableList<Influence> = ArrayList()
        var lastInfluence: Influence? = null

        // We will try to override any session with DIRECT
        var updated = false
        if (channelTrackerByAction != null) {
            lastInfluence = channelTrackerByAction.currentSessionInfluence
            updated = setSessionTracker(channelTrackerByAction, InfluenceType.DIRECT, directId ?: channelTrackerByAction.directId, null)
        }

        if (updated) {
            Logging.debug("InfluenceManager.attemptSessionUpgrade: channel updated, search for ending direct influences on channels: $channelTrackersToReset")
            influencesToEnd.add(lastInfluence!!)
            // Only one session influence channel can be DIRECT at the same time
            // Reset other DIRECT channels, they will init an INDIRECT influence
            // In that way we finish the session duration time for the last influenced session
            for (tracker in channelTrackersToReset) {
                if (tracker.influenceType?.isDirect() == true) {
                    influencesToEnd.add(tracker.currentSessionInfluence)
                    tracker.resetAndInitInfluence()
                }
            }
        }

        Logging.debug("InfluenceManager.attemptSessionUpgrade: try UNATTRIBUTED to INDIRECT upgrade")
        // We will try to override the UNATTRIBUTED session with INDIRECT
        for (channelTracker in channelTrackersToReset) {
            if (channelTracker.influenceType?.isUnattributed() == true) {
                val lastIds = channelTracker.lastReceivedIds
                // There are new ids for attribution and the application was open again without resetting session
                if (lastIds.length() > 0 && !entryAction.isAppClose) {
                    // Save influence to ended it later if needed
                    // This influence will be unattributed
                    val influence = channelTracker.currentSessionInfluence
                    updated = setSessionTracker(channelTracker, InfluenceType.INDIRECT, null, lastIds)
                    // Changed from UNATTRIBUTED to INDIRECT
                    if (updated) influencesToEnd.add(influence)
                }
            }
        }

        Logging.debug("InfluenceManager.attemptSessionUpgrade: Trackers after update attempt: $channels")
        // TODO: FocusTimeController needs the influencesToEnd data.
    }
}
