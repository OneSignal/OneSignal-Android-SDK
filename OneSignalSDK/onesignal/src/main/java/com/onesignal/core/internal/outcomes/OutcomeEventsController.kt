package com.onesignal.core.internal.outcomes

import android.os.Process
import com.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.influence.Influence
import com.onesignal.core.internal.influence.InfluenceChannel
import com.onesignal.core.internal.influence.InfluenceType
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.session.ISessionService
import com.onesignal.core.internal.time.ITime
import com.onesignal.iam.internal.InAppMessageOutcome

internal class OutcomeEventsController(
    private val _session: ISessionService,
    private val outcomeEventsFactory: IOutcomeEventsFactory,
    private val _configModelStore: ConfigModelStore,
    private val _time: ITime,
    private val _deviceService: IDeviceService
) {
    // Keeps track of unique outcome events sent for UNATTRIBUTED sessions on a per session level
    private var unattributedUniqueOutcomeEventsSentOnSession: MutableSet<String>? = null

    init {
        initUniqueOutcomeEventsSentSets()
    }

    /**
     * Init the sets used for tracking attributed and unattributed unique outcome events
     */
    private fun initUniqueOutcomeEventsSentSets() {
        // Get all cached UNATTRIBUTED unique outcomes
        unattributedUniqueOutcomeEventsSentOnSession = mutableSetOf()
        val tempUnattributedUniqueOutcomeEventsSentSet: Set<String>? =
            outcomeEventsFactory.getRepository().getUnattributedUniqueOutcomeEventsSent()
        if (tempUnattributedUniqueOutcomeEventsSentSet != null) {
            unattributedUniqueOutcomeEventsSentOnSession = tempUnattributedUniqueOutcomeEventsSentSet.toMutableSet()
        }
    }

    /**
     * Clean unattributed unique outcome events sent so they can be sent after a new session
     */
    fun cleanOutcomes() {
        Logging.debug("OneSignal cleanOutcomes for session")
        unattributedUniqueOutcomeEventsSentOnSession = mutableSetOf()
        saveUnattributedUniqueOutcomeEvents()
    }

    /**
     * Deletes cached unique outcome notifications whose ids do not exist inside of the NotificationTable.TABLE_NAME
     */
    suspend fun cleanCachedUniqueOutcomes() {
        outcomeEventsFactory.getRepository().cleanCachedUniqueOutcomeEventNotifications(
            OneSignalDbContract.NotificationTable.TABLE_NAME,
            OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID
        )
    }

    /**
     * Any outcomes cached in local DB will be reattempted to be sent again
     * Cached outcomes come from the failure callback of the network request
     */
    suspend fun sendSavedOutcomes() {
        val outcomeEvents: List<OutcomeEventParams> =
            outcomeEventsFactory.getRepository().getSavedOutcomeEvents()
        for (event in outcomeEvents) {
            sendSavedOutcomeEvent(event)
        }
    }

    private suspend fun sendSavedOutcomeEvent(event: OutcomeEventParams) {
        val deviceType: Int = _deviceService.deviceType
        val appId: String = _configModelStore.get().appId

        val response = outcomeEventsFactory.getRepository().requestMeasureOutcomeEvent(appId, deviceType, event)

        if (response?.isSuccess == true) {
            outcomeEventsFactory.getRepository().removeEvent(event)
        }
    }

    suspend fun sendClickActionOutcomes(outcomes: List<InAppMessageOutcome>) {
        for (outcome in outcomes) {
            val name: String = outcome.name
            if (outcome.isUnique) {
                sendUniqueOutcomeEvent(name)
            } else if (outcome.weight > 0) {
                sendOutcomeEventWithValue(name, outcome.weight)
            } else {
                sendOutcomeEvent(name)
            }
        }
    }

    suspend fun sendUniqueOutcomeEvent(name: String): OutcomeEvent? {
        val sessionResult: List<Influence> = _session.influences
        return sendUniqueOutcomeEvent(name, sessionResult)
    }

    suspend fun sendOutcomeEvent(name: String): OutcomeEvent? {
        val influences: List<Influence> = _session.influences
        return sendAndCreateOutcomeEvent(name, 0f, influences)
    }

    suspend fun sendOutcomeEventWithValue(name: String, weight: Float): OutcomeEvent? {
        val influences: List<Influence> = _session.influences
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
            Logging.debug("Unique Outcome disabled for current session")
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
            if (unattributedUniqueOutcomeEventsSentOnSession!!.contains(name)) {
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
            unattributedUniqueOutcomeEventsSentOnSession!!.add(name)
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
                    if (directSourceBody == null) OutcomeSourceBody() else directSourceBody
                )
                InfluenceType.INDIRECT -> indirectSourceBody = setSourceChannelIds(
                    influence,
                    if (indirectSourceBody == null) OutcomeSourceBody() else indirectSourceBody
                )
                InfluenceType.UNATTRIBUTED -> unattributed = true
                InfluenceType.DISABLED -> {
                    Logging.verbose("Outcomes disabled for channel: " + influence.influenceChannel)
                    return null // finish method
                }
            }
        }
        if (directSourceBody == null && indirectSourceBody == null && !unattributed) {
            // Disabled for all channels
            Logging.verbose("Outcomes disabled for all channels")
            return null
        }

        val source = OutcomeSource(directSourceBody, indirectSourceBody)
        val eventParams = OutcomeEventParams(name, source, weight, 0)

        val response = outcomeEventsFactory.getRepository().requestMeasureOutcomeEvent(appId, deviceType, eventParams)

        if (response?.isSuccess == true) {
            saveUniqueOutcome(eventParams)

            // The only case where an actual success has occurred and the OutcomeEvent should be sent back
            return OutcomeEvent.fromOutcomeEventParamsV2toOutcomeEventV1(eventParams)
        } else {
            Thread({
                Thread.currentThread().priority = Process.THREAD_PRIORITY_BACKGROUND
                // Only if we need to save and retry the outcome, then we will save the timestamp for future sending
                eventParams.timestamp = timestampSeconds
                outcomeEventsFactory.getRepository().saveOutcomeEvent(eventParams)
            }, OS_SAVE_OUTCOMES).start()
            Logging.warn(
                """Sending outcome with name: $name failed with status code: ${response?.statusCode} and response: $response
Outcome event was cached and will be reattempted on app cold start"""
            )

            // Return null within the callback to determine not a failure, but not a success in terms of the request made
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
                Logging.debug("Outcomes disabled for channel: " + influence.influenceChannel.toString())
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
        Thread({
            Thread.currentThread().priority = Process.THREAD_PRIORITY_BACKGROUND
            outcomeEventsFactory.getRepository().saveUniqueOutcomeNotifications(eventParams)
        }, OS_SAVE_UNIQUE_OUTCOME_NOTIFICATIONS).start()
    }

    /**
     * Save the current set of UNATTRIBUTED unique outcome names to SharedPrefs
     */
    private fun saveUnattributedUniqueOutcomeEvents() {
        outcomeEventsFactory.getRepository()
            .saveUnattributedUniqueOutcomeEventsSent(unattributedUniqueOutcomeEventsSentOnSession!!)
    }

    /**
     * Get the unique notifications that have not been cached/sent before with the current unique outcome name
     */
    private fun getUniqueIds(name: String, influences: List<Influence>): List<Influence>? {
        val uniqueInfluences: List<Influence> =
            outcomeEventsFactory.getRepository().getNotCachedUniqueOutcome(name, influences)
        return if (uniqueInfluences.size > 0) uniqueInfluences else null
    }

    companion object {
        private const val OS_SAVE_OUTCOMES = "OS_SAVE_OUTCOMES"
        private const val OS_SEND_SAVED_OUTCOMES = "OS_SEND_SAVED_OUTCOMES"
        private const val OS_SAVE_UNIQUE_OUTCOME_NOTIFICATIONS =
            "OS_SAVE_UNIQUE_OUTCOME_NOTIFICATIONS"
        private const val OS_DELETE_CACHED_UNIQUE_OUTCOMES_NOTIFICATIONS_THREAD =
            "OS_DELETE_CACHED_UNIQUE_OUTCOMES_NOTIFICATIONS_THREAD"
    }
}
