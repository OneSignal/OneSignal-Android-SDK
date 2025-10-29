package com.onesignal.session.internal.outcomes.impl

import com.onesignal.common.NetworkUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.influence.IInfluenceManager
import com.onesignal.session.internal.influence.Influence
import com.onesignal.session.internal.influence.InfluenceChannel
import com.onesignal.session.internal.influence.InfluenceType
import com.onesignal.session.internal.outcomes.IOutcomeEventsController
import com.onesignal.session.internal.session.ISessionLifecycleHandler
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.subscriptions.ISubscriptionManager

internal class OutcomeEventsController(
    private val _session: ISessionService,
    private val _influenceManager: IInfluenceManager,
    private val _outcomeEventsCache: IOutcomeEventsRepository,
    private val _outcomeEventsPreferences: IOutcomeEventsPreferences,
    private val _outcomeEventsBackend: IOutcomeEventsBackendService,
    private val _configModelStore: ConfigModelStore,
    private val _identityModelStore: IdentityModelStore,
    private val _subscriptionManager: ISubscriptionManager,
    private val _deviceService: IDeviceService,
    private val _time: ITime,
) : IOutcomeEventsController, IStartableService, ISessionLifecycleHandler {
    // Keeps track of unique outcome events sent for UNATTRIBUTED sessions on a per session level
    private var unattributedUniqueOutcomeEventsSentOnSession: MutableSet<String> = mutableSetOf()

    init {
        unattributedUniqueOutcomeEventsSentOnSession =
            _outcomeEventsPreferences.unattributedUniqueOutcomeEventsSentByChannel?.toMutableSet() ?: mutableSetOf()
        _session.subscribe(this)
    }

    override fun start() {
        suspendifyOnIO {
            sendSavedOutcomes()
            _outcomeEventsCache.cleanCachedUniqueOutcomeEventNotifications()
        }
    }

    override fun onSessionStarted() {
        Logging.debug("OutcomeEventsController.sessionStarted: Cleaning outcomes for new session")
        unattributedUniqueOutcomeEventsSentOnSession = mutableSetOf()
        saveUnattributedUniqueOutcomeEvents()
    }

    override fun onSessionActive() { }

    override fun onSessionEnded(duration: Long) { }

    /**
     * Any outcomes cached in local DB will be reattempted to be sent again
     * Cached outcomes come from the failure callback of the network request
     */
    private suspend fun sendSavedOutcomes() {
        val outcomeEvents: List<OutcomeEventParams> =
            _outcomeEventsCache.getAllEventsToSend()
        for (event in outcomeEvents) {
            sendSavedOutcomeEvent(event)
        }
    }

    private suspend fun sendSavedOutcomeEvent(event: OutcomeEventParams) {
        try {
            requestMeasureOutcomeEvent(event)

            _outcomeEventsCache.deleteOldOutcomeEvent(event)
        } catch (ex: BackendException) {
            val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)
            val err = "OutcomeEventsController.sendSavedOutcomeEvent: Sending outcome with name: ${event.outcomeId} failed with status code: ${ex.statusCode} and response: ${ex.response}"

            if (responseType == NetworkUtils.ResponseStatusType.RETRYABLE) {
                Logging.warn("$err Outcome event was cached and will be reattempted on app cold start")
            } else {
                Logging.error("$err Outcome event will be omitted!")
                _outcomeEventsCache.deleteOldOutcomeEvent(event)
            }
        }
    }

    override suspend fun sendSessionEndOutcomeEvent(duration: Long): OutcomeEvent? {
        val influences: List<Influence> = _influenceManager.influences

        // only send the outcome if there are any influences associated with the session
        for (influence in influences) {
            if (influence.ids != null) {
                return sendAndCreateOutcomeEvent("os__session_duration", 0f, duration, influences)
            }
        }
        return null
    }

    override suspend fun sendUniqueOutcomeEvent(name: String): OutcomeEvent? {
        val sessionResult: List<Influence> = _influenceManager.influences
        return sendUniqueOutcomeEvent(name, sessionResult)
    }

    override suspend fun sendOutcomeEvent(name: String): OutcomeEvent? {
        val influences: List<Influence> = _influenceManager.influences
        return sendAndCreateOutcomeEvent(name, 0f, 0, influences)
    }

    override suspend fun sendOutcomeEventWithValue(
        name: String,
        weight: Float,
    ): OutcomeEvent? {
        val influences: List<Influence> = _influenceManager.influences
        return sendAndCreateOutcomeEvent(name, weight, 0, influences)
    }

    /**
     * An unique outcome is considered unattributed when all channels are unattributed
     * If one channel is attributed is enough reason to cache attribution
     */
    private suspend fun sendUniqueOutcomeEvent(
        name: String,
        sessionInfluences: List<Influence>,
    ): OutcomeEvent? {
        val influences: List<Influence> = removeDisabledInfluences(sessionInfluences)
        if (influences.isEmpty()) {
            Logging.debug("OutcomeEventsController.sendUniqueOutcomeEvent: Unique Outcome disabled for current session")
            return null
        }
        var attributed = false
        for (influence in influences) {
            if (influence.influenceType.isAttributed()) {
                // At least one channel attributed this outcome
                attributed = true
                break
            }
        }

        // Special handling for unique outcomes in the attributed and unattributed scenarios
        if (attributed) {
            // Make sure unique Ids exist before trying to make measure request
            val uniqueInfluences: List<Influence>? = getUniqueIds(name, influences)
            if (uniqueInfluences == null) {
                Logging.debug(
                    """
                    Measure endpoint will not send because unique outcome already sent for:
                    SessionInfluences: $influences
                    Outcome name: $name
                    """.trimIndent(),
                )

                // Return null to determine not a failure, but not a success in terms of the request made
                return null
            }
            return sendAndCreateOutcomeEvent(name, 0f, 0, uniqueInfluences)
        } else {
            // Make sure unique outcome has not been sent for current unattributed session
            if (unattributedUniqueOutcomeEventsSentOnSession.contains(name)) {
                Logging.debug(
                    """
                    Measure endpoint will not send because unique outcome already sent for:
                    Session: ${InfluenceType.UNATTRIBUTED}
                    Outcome name: $name
                    """.trimIndent(),
                )

                // Return null to determine not a failure, but not a success in terms of the request made
                return null
            }
            unattributedUniqueOutcomeEventsSentOnSession.add(name)
            return sendAndCreateOutcomeEvent(name, 0f, 0, influences)
        }
    }

    private suspend fun sendAndCreateOutcomeEvent(
        name: String,
        weight: Float,
        // Note: this is optional
        sessionTime: Long,
        influences: List<Influence>,
    ): OutcomeEvent? {
        val timestampSeconds: Long = _time.currentTimeMillis / 1000
        var directSourceBody: OutcomeSourceBody? = null
        var indirectSourceBody: OutcomeSourceBody? = null
        var unattributed = false
        for (influence in influences) {
            when (influence.influenceType) {
                InfluenceType.DIRECT ->
                    directSourceBody =
                        setSourceChannelIds(
                            influence,
                            directSourceBody ?: OutcomeSourceBody(),
                        )
                InfluenceType.INDIRECT ->
                    indirectSourceBody =
                        setSourceChannelIds(
                            influence,
                            indirectSourceBody ?: OutcomeSourceBody(),
                        )
                InfluenceType.UNATTRIBUTED -> unattributed = true
                InfluenceType.DISABLED -> {
                    Logging.verbose(
                        "OutcomeEventsController.sendAndCreateOutcomeEvent: Outcomes disabled for channel: " + influence.influenceChannel,
                    )
                }
            }
        }
        if (directSourceBody == null && indirectSourceBody == null && !unattributed) {
            // Disabled for all channels
            Logging.verbose("OutcomeEventsController.sendAndCreateOutcomeEvent: Outcomes disabled for all channels")
            return null
        }

        val source = OutcomeSource(directSourceBody, indirectSourceBody)
        val eventParams = OutcomeEventParams(name, source, weight, sessionTime, 0)

        try {
            requestMeasureOutcomeEvent(eventParams)

            saveUniqueOutcome(eventParams)

            // The only case where an actual success has occurred and the OutcomeEvent should be sent back
            return OutcomeEvent.fromOutcomeEventParamstoOutcomeEvent(eventParams)
        } catch (ex: BackendException) {
            val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)
            val err = "OutcomeEventsController.sendAndCreateOutcomeEvent: Sending outcome with name: $name failed with status code: ${ex.statusCode} and response: ${ex.response}"

            if (responseType == NetworkUtils.ResponseStatusType.RETRYABLE) {
                Logging.warn("$err Outcome event was cached and will be reattempted on app cold start")

                // Only if we need to save and retry the outcome, then we will save the timestamp for future sending
                eventParams.timestamp = timestampSeconds
                _outcomeEventsCache.saveOutcomeEvent(eventParams)
            } else {
                Logging.error("$err Outcome event will be omitted!")
                _outcomeEventsCache.deleteOldOutcomeEvent(eventParams)
            }

            // Return null to determine not a failure, but not a success in terms of the request made
            return null
        }
    }

    private fun setSourceChannelIds(
        influence: Influence,
        sourceBody: OutcomeSourceBody,
    ): OutcomeSourceBody {
        when (influence.influenceChannel) {
            InfluenceChannel.IAM -> sourceBody.inAppMessagesIds = influence.ids
            InfluenceChannel.NOTIFICATION -> sourceBody.notificationIds = influence.ids
        }
        return sourceBody
    }

    private fun removeDisabledInfluences(influences: List<Influence>): List<Influence> {
        val availableInfluences: MutableList<Influence> = influences.toMutableList()
        for (influence in influences) {
            if (influence.influenceType.isDisabled()) {
                Logging.debug(
                    "OutcomeEventsController.removeDisabledInfluences: Outcomes disabled for channel: " + influence.influenceChannel.toString(),
                )
                availableInfluences.remove(influence)
            }
        }
        return availableInfluences
    }

    private fun saveUniqueOutcome(eventParams: OutcomeEventParams) {
        if (eventParams.isUnattributed()) {
            saveUnattributedUniqueOutcomeEvents()
        } else {
            saveAttributedUniqueOutcomeNotifications(
                eventParams,
            )
        }
    }

    /**
     * Save the ATTRIBUTED JSONArray of notification ids with unique outcome names to SQL
     */
    private fun saveAttributedUniqueOutcomeNotifications(eventParams: OutcomeEventParams) {
        suspendifyOnIO {
            _outcomeEventsCache.saveUniqueOutcomeEventParams(eventParams)
        }
    }

    /**
     * Save the current set of UNATTRIBUTED unique outcome names to SharedPrefs
     */
    private fun saveUnattributedUniqueOutcomeEvents() {
        _outcomeEventsPreferences.unattributedUniqueOutcomeEventsSentByChannel = unattributedUniqueOutcomeEventsSentOnSession
    }

    /**
     * Get the unique notifications that have not been cached/sent before with the current unique outcome name
     */
    private suspend fun getUniqueIds(
        name: String,
        influences: List<Influence>,
    ): List<Influence>? {
        val uniqueInfluences: List<Influence> =
            _outcomeEventsCache.getNotCachedUniqueInfluencesForOutcome(name, influences)
        return uniqueInfluences.ifEmpty { null }
    }

    private suspend fun requestMeasureOutcomeEvent(eventParams: OutcomeEventParams) {
        val appId: String = _configModelStore.model.appId
        val subscriptionId = _subscriptionManager.subscriptions.push.id
        val deviceType = SubscriptionObjectType.fromDeviceType(_deviceService.deviceType).value

        // if we don't have a subscription ID yet, throw an exception. The outcome will be saved and processed
        // later, when we do have a subscription ID.
        if (subscriptionId.isEmpty() || deviceType.isEmpty()) {
            throw BackendException(0)
        }

        val event = OutcomeEvent.fromOutcomeEventParamstoOutcomeEvent(eventParams)
        val direct =
            when (event.session) {
                InfluenceType.DIRECT -> true
                InfluenceType.INDIRECT -> false
                InfluenceType.UNATTRIBUTED -> null
                else -> null
            }

        _outcomeEventsBackend.sendOutcomeEvent(appId, _identityModelStore.model.onesignalId, subscriptionId, deviceType, direct, event)
    }
}
