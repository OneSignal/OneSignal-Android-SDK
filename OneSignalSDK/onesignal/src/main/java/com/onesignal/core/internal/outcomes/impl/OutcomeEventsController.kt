package com.onesignal.core.internal.outcomes.impl

import android.os.Process
import com.onesignal.core.internal.common.suspendifyOnThread
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.http.HttpResponse
import com.onesignal.core.internal.influence.IInfluenceManager
import com.onesignal.core.internal.influence.Influence
import com.onesignal.core.internal.influence.InfluenceChannel
import com.onesignal.core.internal.influence.InfluenceType
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.outcomes.IOutcomeEventsController
import com.onesignal.core.internal.session.ISessionLifecycleHandler
import com.onesignal.core.internal.session.ISessionService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime

internal class OutcomeEventsController(
    private val _session: ISessionService,
    private val _influenceManager: IInfluenceManager,
    private val _outcomeEventsCache: IOutcomeEventsRepository,
    private val _outcomeEventsPreferences: IOutcomeEventsPreferences,
    private val _outcomeEventsBackend: IOutcomeEventsBackend,
    private val _configModelStore: ConfigModelStore,
    private val _time: ITime,
    private val _deviceService: IDeviceService
) : IOutcomeEventsController, IStartableService, ISessionLifecycleHandler {
    // Keeps track of unique outcome events sent for UNATTRIBUTED sessions on a per session level
    private var unattributedUniqueOutcomeEventsSentOnSession: MutableSet<String> = mutableSetOf()

    init {
        unattributedUniqueOutcomeEventsSentOnSession =
            _outcomeEventsPreferences.unattributedUniqueOutcomeEventsSentByChannel?.toMutableSet() ?: mutableSetOf()
        _session.subscribe(this)
    }

    override fun start() {
        suspendifyOnThread {
            sendSavedOutcomes()
            _outcomeEventsCache.cleanCachedUniqueOutcomeEventNotifications()
        }
    }

    override fun sessionStarted() {
        Logging.debug("OutcomeEventsController.sessionStarted: Cleaning outcomes for new session")
        unattributedUniqueOutcomeEventsSentOnSession = mutableSetOf()
        saveUnattributedUniqueOutcomeEvents()
    }

    override fun sessionResumed() { }

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
        val deviceType: Int = _deviceService.deviceType
        val appId: String = _configModelStore.get().appId

        val response = requestMeasureOutcomeEvent(appId, deviceType, event)

        if (response?.isSuccess == true) {
            _outcomeEventsCache.deleteOldOutcomeEvent(event)
        }
    }

    override suspend fun sendUniqueOutcomeEvent(name: String): OutcomeEvent? {
        val sessionResult: List<Influence> = _influenceManager.influences
        return sendUniqueOutcomeEvent(name, sessionResult)
    }

    override suspend fun sendOutcomeEvent(name: String): OutcomeEvent? {
        val influences: List<Influence> = _influenceManager.influences
        return sendAndCreateOutcomeEvent(name, 0f, influences)
    }

    override suspend fun sendOutcomeEventWithValue(name: String, weight: Float): OutcomeEvent? {
        val influences: List<Influence> = _influenceManager.influences
        return sendAndCreateOutcomeEvent(name, weight, influences)
    }

    /**
     * An unique outcome is considered unattributed when all channels are unattributed
     * If one channel is attributed is enough reason to cache attribution
     */
    private suspend fun sendUniqueOutcomeEvent(
        name: String,
        sessionInfluences: List<Influence>
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
                    """.trimIndent()
                )

                // Return null to determine not a failure, but not a success in terms of the request made
                return null
            }
            return sendAndCreateOutcomeEvent(name, 0f, uniqueInfluences)
        } else {
            // Make sure unique outcome has not been sent for current unattributed session
            if (unattributedUniqueOutcomeEventsSentOnSession.contains(name)) {
                Logging.debug(
                    """
                        Measure endpoint will not send because unique outcome already sent for: 
                        Session: ${InfluenceType.UNATTRIBUTED}
                        Outcome name: $name
                    """.trimIndent()
                )

                // Return null to determine not a failure, but not a success in terms of the request made
                return null
            }
            unattributedUniqueOutcomeEventsSentOnSession.add(name)
            return sendAndCreateOutcomeEvent(name, 0f, influences)
        }
    }

    private suspend fun sendAndCreateOutcomeEvent(
        name: String,
        weight: Float,
        influences: List<Influence>
    ): OutcomeEvent? {
        val timestampSeconds: Long = _time.currentTimeMillis / 1000
        val deviceType: Int = _deviceService.deviceType
        val appId: String = _configModelStore.get().appId
        var directSourceBody: OutcomeSourceBody? = null
        var indirectSourceBody: OutcomeSourceBody? = null
        var unattributed = false
        for (influence in influences) {
            when (influence.influenceType) {
                InfluenceType.DIRECT -> directSourceBody = setSourceChannelIds(
                    influence,
                    directSourceBody ?: OutcomeSourceBody()
                )
                InfluenceType.INDIRECT -> indirectSourceBody = setSourceChannelIds(
                    influence,
                    indirectSourceBody ?: OutcomeSourceBody()
                )
                InfluenceType.UNATTRIBUTED -> unattributed = true
                InfluenceType.DISABLED -> {
                    Logging.verbose("OutcomeEventsController.sendAndCreateOutcomeEvent: Outcomes disabled for channel: " + influence.influenceChannel)
                    return null // finish method
                }
            }
        }
        if (directSourceBody == null && indirectSourceBody == null && !unattributed) {
            // Disabled for all channels
            Logging.verbose("OutcomeEventsController.sendAndCreateOutcomeEvent: Outcomes disabled for all channels")
            return null
        }

        val source = OutcomeSource(directSourceBody, indirectSourceBody)
        val eventParams = OutcomeEventParams(name, source, weight, 0)

        val response = requestMeasureOutcomeEvent(appId, deviceType, eventParams)

        if (response?.isSuccess == true) {
            saveUniqueOutcome(eventParams)

            // The only case where an actual success has occurred and the OutcomeEvent should be sent back
            return OutcomeEvent.fromOutcomeEventParamstoOutcomeEvent(eventParams)
        } else {
            Logging.warn(
                """OutcomeEventsController.sendAndCreateOutcomeEvent: Sending outcome with name: $name failed with status code: ${response?.statusCode} and response: $response
Outcome event was cached and will be reattempted on app cold start"""
            )

            // Only if we need to save and retry the outcome, then we will save the timestamp for future sending
            eventParams.timestamp = timestampSeconds
            _outcomeEventsCache.saveOutcomeEvent(eventParams)

            // Return null to determine not a failure, but not a success in terms of the request made
            return null
        }
    }

    private fun setSourceChannelIds(
        influence: Influence,
        sourceBody: OutcomeSourceBody
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
                Logging.debug("OutcomeEventsController.removeDisabledInfluences: Outcomes disabled for channel: " + influence.influenceChannel.toString())
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
                eventParams
            )
        }
    }

    /**
     * Save the ATTRIBUTED JSONArray of notification ids with unique outcome names to SQL
     */
    private fun saveAttributedUniqueOutcomeNotifications(eventParams: OutcomeEventParams) {
        suspendifyOnThread(Process.THREAD_PRIORITY_BACKGROUND) {
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
    private suspend fun getUniqueIds(name: String, influences: List<Influence>): List<Influence>? {
        val uniqueInfluences: List<Influence> =
            _outcomeEventsCache.getNotCachedUniqueInfluencesForOutcome(name, influences)
        return uniqueInfluences.ifEmpty { null }
    }

    private suspend fun requestMeasureOutcomeEvent(appId: String, deviceType: Int, eventParams: OutcomeEventParams): HttpResponse? {
        val event = OutcomeEvent.fromOutcomeEventParamstoOutcomeEvent(eventParams)
        val direct = when (event.session) {
            InfluenceType.DIRECT -> true
            InfluenceType.INDIRECT -> false
            InfluenceType.UNATTRIBUTED -> null
            else -> null
        }

        return _outcomeEventsBackend.sendOutcomeEvent(appId, deviceType, direct, event)
    }
}
