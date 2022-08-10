package com.onesignal.onesignal.core.internal.session

import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.application.IApplicationLifecycleHandler
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.common.JSONUtils
import com.onesignal.onesignal.core.internal.common.events.EventProducer
import com.onesignal.onesignal.core.internal.common.events.IEventNotifier
import com.onesignal.onesignal.core.internal.influence.IChannelTracker
import com.onesignal.onesignal.core.internal.influence.IInfluenceManager
import com.onesignal.onesignal.core.internal.influence.Influence
import com.onesignal.onesignal.core.internal.influence.InfluenceType
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.models.ConfigModel
import com.onesignal.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.onesignal.core.internal.models.SessionModel
import com.onesignal.onesignal.core.internal.models.SessionModelStore
import com.onesignal.onesignal.core.internal.startup.IStartableService
import org.json.JSONArray
import java.util.*

internal class SessionService(
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
    private val _sessionModelStore: SessionModelStore,
    private val _influenceManager: IInfluenceManager,
) : ISessionService, IStartableService, IApplicationLifecycleHandler, IEventNotifier<ISessionLifecycleHandler>  {

    override val influences: List<Influence>
        get() = _influenceManager.influences

    override val sessionInfluences: List<Influence>
        get() = _influenceManager.sessionInfluences

    private val _sessionLifeCycleNotifier: EventProducer<ISessionLifecycleHandler> = EventProducer()
    private var _focusOutTime: Date = Calendar.getInstance().time
    private var _session: SessionModel? = null
    private var _config: ConfigModel? = null

    override fun start() {
        _session = _sessionModelStore.get()
        _config = _configModelStore.get()
        _applicationService.addApplicationLifecycleHandler(this)

        // TODO: Determine if we need to create a new session
    }

    override fun onFocus() {
        Logging.log(LogLevel.DEBUG, "SessionService.onFocus()")
        val now: Date = Calendar.getInstance().time

        var dt = now.time - _focusOutTime.time;

        if (dt > (_config!!.sessionFocusTimeout * 60 * 1000)) {
            Logging.debug("Session timeout reached");
            createNewSession();
        }
        else if (dt < 0) {
            // user is messing with system clock
            Logging.debug("System clock changed to earlier than focus out time");
            createNewSession();
        }
        else { // just add to the unfocused duration
            _session!!.unfocusedDuration += dt;
        }
    }

    override fun onUnfocused() {
        Logging.log(LogLevel.DEBUG, "SessionService.onUnfocused()")
        _focusOutTime = Calendar.getInstance().time;
    }

    private fun createNewSession() {
        // no reason to maintain old session models, just overwrite
        _session!!.id = UUID.randomUUID().toString()
        _session!!.startTime = Calendar.getInstance().time
        _session!!.unfocusedDuration = 0.0;

        Logging.debug("New session started at ${_session!!.startTime}");
        _sessionLifeCycleNotifier.fire { it.sessionStarted() }
    }

    override fun subscribe(handler: ISessionLifecycleHandler) = _sessionLifeCycleNotifier.subscribe(handler)

    override fun unsubscribe(handler: ISessionLifecycleHandler) = _sessionLifeCycleNotifier.unsubscribe(handler)

    override fun onNotificationReceived(notificationId: String) {
        Logging.debug("OneSignal SessionManager onNotificationReceived notificationId: $notificationId")

        if (notificationId.isEmpty())
            return

        _influenceManager.notificationChannelTracker.saveLastId(notificationId)
    }

    override fun onInAppMessageReceived(messageId: String) {
        Logging.debug("OneSignal SessionManager onInAppMessageReceived messageId: $messageId")
        val inAppMessageTracker = _influenceManager.iAMChannelTracker
        inAppMessageTracker.saveLastId(messageId)
        inAppMessageTracker.resetAndInitInfluence()
    }

    override fun directInfluenceFromIAMClick(messageId: String) {
        Logging.debug("OneSignal SessionManager onDirectInfluenceFromIAMClick messageId: $messageId")
        val inAppMessageTracker =
        // We don't care about ending the session duration because IAM doesn't influence a session
        // We don't care about ending the session duration because IAM doesn't influence a session
        setSession(_influenceManager.iAMChannelTracker, InfluenceType.DIRECT, messageId, null)
    }

    override fun directInfluenceFromIAMClickFinished() {
        Logging.debug("OneSignal SessionManager onDirectInfluenceFromIAMClickFinished")
        val inAppMessageTracker = _influenceManager.iAMChannelTracker
        inAppMessageTracker.resetAndInitInfluence()
    }

    // Call when the session for the app changes, caches the state, and broadcasts the session that just ended
    private fun setSession(channelTracker: IChannelTracker, influenceType: InfluenceType, directNotificationId: String?, indirectNotificationIds: JSONArray?): Boolean {
        if (!willChangeSession(channelTracker, influenceType, directNotificationId, indirectNotificationIds))
            return false
        Logging.debug("""
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
        Logging.debug("Trackers changed to: " + _influenceManager.channels.toString())
        // Session changed
        return true
    }

    private fun willChangeSession(channelTracker: IChannelTracker, influenceType: InfluenceType, directNotificationId: String?, indirectNotificationIds: JSONArray?): Boolean {
        if (influenceType != channelTracker.influenceType)
            return true

        val channelInfluenceType = channelTracker.influenceType

        // Allow updating a direct session to a new direct when a new notification is clicked
        return if (channelInfluenceType!!.isDirect() && channelTracker.directId != null &&
            channelTracker.directId != directNotificationId
        ) {
            true
        } else
            channelInfluenceType.isIndirect() && channelTracker.indirectIds != null && channelTracker.indirectIds!!.length() > 0 &&
                !JSONUtils.compareJSONArrays(channelTracker.indirectIds, indirectNotificationIds)

        // Allow updating an indirect session to a new indirect when a new notification is received
    }
}
