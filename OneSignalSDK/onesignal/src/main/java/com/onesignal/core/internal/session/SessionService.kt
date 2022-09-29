package com.onesignal.core.internal.session

import com.onesignal.core.debug.LogLevel
import com.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.common.events.EventProducer
import com.onesignal.core.internal.common.events.IEventNotifier
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.models.ConfigModel
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.models.SessionModel
import com.onesignal.core.internal.models.SessionModelStore
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import java.util.UUID

internal class SessionService(
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
    private val _sessionModelStore: SessionModelStore,
    private val _time: ITime
) : ISessionService, IStartableService, IApplicationLifecycleHandler, IEventNotifier<ISessionLifecycleHandler> {

    override val startTime: Long
        get() = _session!!.startTime

    private val _sessionLifeCycleNotifier: EventProducer<ISessionLifecycleHandler> = EventProducer()
    private var _focusOutTime: Long = 0
    private var _session: SessionModel? = null
    private var _config: ConfigModel? = null

    override fun start() {
        _session = _sessionModelStore.get()
        _config = _configModelStore.get()
        _applicationService.addApplicationLifecycleHandler(this)
        createNewSession()
    }

    override fun onFocus() {
        Logging.log(LogLevel.DEBUG, "SessionService.onFocus()")

        val now = _time.currentTimeMillis
        var dt = now - _focusOutTime

        if (dt > (_config!!.sessionFocusTimeout * 60 * 1000)) {
            Logging.debug("SessionService: Session timeout reached")
            createNewSession()
        } else if (dt < 0) {
            // user is messing with system clock
            Logging.debug("SessionService: System clock changed to earlier than focus out time")
            createNewSession()
        } else { // just add to the unfocused duration
            _session!!.unfocusedDuration += dt
            _sessionLifeCycleNotifier.fire { it.sessionResumed() }
        }
    }

    override fun onUnfocused() {
        Logging.log(LogLevel.DEBUG, "SessionService.onUnfocused()")
        _focusOutTime = _time.currentTimeMillis
    }

    private fun createNewSession() {
        // no reason to maintain old session models, just overwrite
        _session!!.sessionId = UUID.randomUUID().toString()
        _session!!.startTime = _time.currentTimeMillis
        _session!!.unfocusedDuration = 0.0

        Logging.debug("SessionService: New session started at ${_session!!.startTime}")
        _sessionLifeCycleNotifier.fire { it.sessionStarted() }
    }

    override fun subscribe(handler: ISessionLifecycleHandler) = _sessionLifeCycleNotifier.subscribe(handler)

    override fun unsubscribe(handler: ISessionLifecycleHandler) = _sessionLifeCycleNotifier.unsubscribe(handler)

    override fun onNotificationReceived(notificationId: String) {
        Logging.debug("SessionService.onNotificationReceived notificationId: $notificationId")

        if (notificationId.isEmpty()) {
            return
        }

        _influenceManager.notificationChannelTracker.saveLastId(notificationId)
    }

    override fun onInAppMessageReceived(messageId: String) {
        Logging.debug("SessionService.onInAppMessageReceived messageId: $messageId")
        val inAppMessageTracker = _influenceManager.iAMChannelTracker
        inAppMessageTracker.saveLastId(messageId)
        inAppMessageTracker.resetAndInitInfluence()
    }

    override fun onDirectInfluenceFromNotificationOpen(entryAction: AppEntryAction, notificationId: String?) {
        Logging.debug("SessionService.onDirectInfluenceFromNotificationOpen entryAction: $entryAction, notificationId: $notificationId")

        if (notificationId == null || notificationId.isEmpty()) {
            return
        }

        attemptSessionUpgrade(entryAction, notificationId)
    }

    override fun directInfluenceFromIAMClick(messageId: String) {
        Logging.debug("SessionService.onDirectInfluenceFromIAMClick messageId: $messageId")
        val inAppMessageTracker =
            // We don't care about ending the session duration because IAM doesn't influence a session
            // We don't care about ending the session duration because IAM doesn't influence a session
            setSession(_influenceManager.iAMChannelTracker, InfluenceType.DIRECT, messageId, null)
    }

    override fun directInfluenceFromIAMClickFinished() {
        Logging.debug("SessionService.directInfluenceFromIAMClickFinished")
        val inAppMessageTracker = _influenceManager.iAMChannelTracker
        inAppMessageTracker.resetAndInitInfluence()
    }

    // TODO: This needs to be driven (on startup?)
    fun restartSessionIfNeeded(entryAction: AppEntryAction) {
        val channelTrackers: List<IChannelTracker> = _influenceManager.getChannelsToResetByEntryAction(entryAction)
        val updatedInfluences: MutableList<Influence> = ArrayList()
        Logging.debug("SessionService.restartSessionIfNeeded(entryAction: $entryAction)\n channelTrackers: $channelTrackers")

        for (channelTracker in channelTrackers) {
            val lastIds = channelTracker.lastReceivedIds
            Logging.debug("SessionService.restartSessionIfNeeded: lastIds: $lastIds")
            val influence = channelTracker.currentSessionInfluence
            var updated: Boolean = if (lastIds.length() > 0) {
                setSession(
                    channelTracker,
                    InfluenceType.INDIRECT,
                    null,
                    lastIds
                )
            } else {
                setSession(channelTracker, InfluenceType.UNATTRIBUTED, null, null)
            }
            if (updated) updatedInfluences.add(influence)
        }
        sendSessionEndingWithInfluences(updatedInfluences)
    }

    // Call when the session for the app changes, caches the state, and broadcasts the session that just ended
    private fun setSession(channelTracker: IChannelTracker, influenceType: InfluenceType, directNotificationId: String?, indirectNotificationIds: JSONArray?): Boolean {
        if (!willChangeSession(channelTracker, influenceType, directNotificationId, indirectNotificationIds)) {
            return false
        }
        Logging.debug(
            """
            OSChannelTracker changed: ${channelTracker.idTag}
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
        Logging.debug("SessionService: Trackers changed to: " + _influenceManager.channels.toString())
        // Session changed
        return true
    }

    private fun willChangeSession(channelTracker: IChannelTracker, influenceType: InfluenceType, directNotificationId: String?, indirectNotificationIds: JSONArray?): Boolean {
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

    private fun attemptSessionUpgrade(entryAction: AppEntryAction, directId: String?) {
        Logging.debug("SessionService.attemptSessionUpgrade(entryAction: $entryAction, directId: $directId)")
        val channelTrackerByAction = _influenceManager.getChannelByEntryAction(entryAction)
        val channelTrackersToReset = _influenceManager.getChannelsToResetByEntryAction(entryAction)
        val influencesToEnd: MutableList<Influence> = ArrayList()
        var lastInfluence: Influence? = null

        // We will try to override any session with DIRECT
        var updated = false
        if (channelTrackerByAction != null) {
            lastInfluence = channelTrackerByAction.currentSessionInfluence
            updated = setSession(channelTrackerByAction, InfluenceType.DIRECT, directId ?: channelTrackerByAction.directId, null)
        }

        if (updated) {
            Logging.debug("SessionService.attemptSessionUpgrade: channel updated, search for ending direct influences on channels: $channelTrackersToReset")
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

        Logging.debug("SessionService.attemptSessionUpgrade: try UNATTRIBUTED to INDIRECT upgrade")
        // We will try to override the UNATTRIBUTED session with INDIRECT
        for (channelTracker in channelTrackersToReset) {
            if (channelTracker.influenceType?.isUnattributed() == true) {
                val lastIds = channelTracker.lastReceivedIds
                // There are new ids for attribution and the application was open again without resetting session
                if (lastIds.length() > 0 && !entryAction.isAppClose) {
                    // Save influence to ended it later if needed
                    // This influence will be unattributed
                    val influence = channelTracker.currentSessionInfluence
                    updated = setSession(channelTracker, InfluenceType.INDIRECT, null, lastIds)
                    // Changed from UNATTRIBUTED to INDIRECT
                    if (updated) influencesToEnd.add(influence)
                }
            }
        }

        Logging.debug("SessionService.attemptSessionUpgrade: Trackers after update attempt: " + _influenceManager.channels.toString())
        sendSessionEndingWithInfluences(influencesToEnd)
    }

    private fun sendSessionEndingWithInfluences(endingInfluences: List<Influence>) {
        Logging.debug("SessionService.sendSessionEndingWithInfluences(endingInfluences:: $endingInfluences)")

        // Only end session if there are influences available to end
        if (endingInfluences.isNotEmpty()) {
            _sessionLifeCycleNotifier.fire { it.sessionEnding(endingInfluences) }
        }
    }
}
