package com.onesignal.core.internal.outcomes.impl

import android.content.ContentValues
import android.database.Cursor
import androidx.annotation.WorkerThread
import com.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.core.internal.influence.Influence
import com.onesignal.core.internal.influence.InfluenceChannel
import com.onesignal.core.internal.influence.InfluenceType
import com.onesignal.core.internal.influence.InfluenceType.Companion.fromString
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.outcomes.IOutcomeEventsCache
import com.onesignal.core.internal.outcomes.OutcomeConstants
import com.onesignal.core.internal.outcomes.OutcomeEventParams
import com.onesignal.core.internal.outcomes.OutcomeSource
import com.onesignal.core.internal.outcomes.OutcomeSourceBody
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.util.Locale

internal class OSOutcomeEventsCache(
    private val dbHelper: IDatabaseProvider,
    private val preferences: IPreferencesService
) : IOutcomeEventsCache {

    override val isOutcomesV2ServiceEnabled: Boolean
        get() = preferences.getBool(
            PreferenceStores.ONESIGNAL,
            PreferenceOneSignalKeys.PREFS_OS_OUTCOMES_V2,
            false
        )!!

    override val unattributedUniqueOutcomeEventsSentByChannel: Set<String>?
        get() = preferences.getStringSet(
            PreferenceStores.ONESIGNAL,
            OutcomeConstants.PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT,
            null
        )

    override fun saveUnattributedUniqueOutcomeEventsSentByChannel(unattributedUniqueOutcomeEvents: Set<String>?) {
        preferences.saveStringSet(
            PreferenceStores.ONESIGNAL,
            OutcomeConstants.PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT, // Post success, store unattributed unique outcome event names
            unattributedUniqueOutcomeEvents!!
        )
    }

    /**
     * Delete event from the DB
     */
    @WorkerThread
    @Synchronized
    override fun deleteOldOutcomeEvent(event: OutcomeEventParams) {
        dbHelper.get().delete(OutcomeEventsTable.TABLE_NAME, OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + " = ?", arrayOf(event.timestamp.toString()))
    }

    /**
     * Save an outcome event to send it on the future
     * For offline mode and contingency of errors
     */
    @WorkerThread
    @Synchronized
    override fun saveOutcomeEvent(eventParams: OutcomeEventParams) {
        var notificationIds = JSONArray()
        var iamIds = JSONArray()
        var notificationInfluenceType = InfluenceType.UNATTRIBUTED
        var iamInfluenceType = InfluenceType.UNATTRIBUTED
        // Check for direct channels
        eventParams.outcomeSource?.directBody?.let { directBody ->
            directBody.notificationIds?.let {
                if (it.length() > 0) {
                    notificationInfluenceType = InfluenceType.DIRECT
                    notificationIds = it
                }
            }
            directBody.inAppMessagesIds?.let {
                if (it.length() > 0) {
                    iamInfluenceType = InfluenceType.DIRECT
                    iamIds = it
                }
            }
        }
        // Check for indirect channels
        eventParams.outcomeSource?.indirectBody?.let { indirectBody ->
            indirectBody.notificationIds?.let {
                if (it.length() > 0) {
                    notificationInfluenceType = InfluenceType.INDIRECT
                    notificationIds = it
                }
            }
            indirectBody.inAppMessagesIds?.let {
                if (it.length() > 0) {
                    iamInfluenceType = InfluenceType.INDIRECT
                    iamIds = it
                }
            }
        }
        ContentValues().apply {
            // Save influence ids
            put(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS, notificationIds.toString())
            put(OutcomeEventsTable.COLUMN_NAME_IAM_IDS, iamIds.toString())
            // Save influence types
            put(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE, notificationInfluenceType.toString().toLowerCase())
            put(OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE, iamInfluenceType.toString().toLowerCase())
            // Save outcome data
            put(OutcomeEventsTable.COLUMN_NAME_NAME, eventParams.outcomeId)
            put(OutcomeEventsTable.COLUMN_NAME_WEIGHT, eventParams.weight)
            put(OutcomeEventsTable.COLUMN_NAME_TIMESTAMP, eventParams.timestamp)
        }.also { values ->
            dbHelper.get().insert(OutcomeEventsTable.TABLE_NAME, null, values)
        }
    }

    /**
     * Save an outcome event to send it on the future
     * For offline mode and contingency of errors
     */
    @WorkerThread
    @Synchronized
    override fun getAllEventsToSend(): List<OutcomeEventParams> {
        val events: MutableList<OutcomeEventParams> = ArrayList()
        var cursor: Cursor? = null
        try {
            cursor = dbHelper.get().query(
                OutcomeEventsTable.TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                null
            )
            with(cursor) {
                if (moveToFirst()) {
                    do {
                        // Retrieve influence types
                        val notificationInfluenceTypeString = getString(getColumnIndex(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE))
                        val notificationInfluenceType = fromString(notificationInfluenceTypeString)
                        val iamInfluenceTypeString = getString(getColumnIndex(OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE))
                        val iamInfluenceType = fromString(iamInfluenceTypeString)

                        // Retrieve influence ids
                        val notificationIds = getString(getColumnIndex(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS))
                            ?: "[]"
                        val iamIds = getString(getColumnIndex(OutcomeEventsTable.COLUMN_NAME_IAM_IDS))
                            ?: "[]"

                        // Retrieve Outcome data
                        val name = getString(getColumnIndex(OutcomeEventsTable.COLUMN_NAME_NAME))
                        val weight = getFloat(getColumnIndex(OutcomeEventsTable.COLUMN_NAME_WEIGHT))
                        val timestamp = getLong(getColumnIndex(OutcomeEventsTable.COLUMN_NAME_TIMESTAMP))

                        try {
                            val directSourceBody = OutcomeSourceBody()
                            val indirectSourceBody = OutcomeSourceBody()
                            val source: OutcomeSource = getNotificationInfluenceSource(notificationInfluenceType, directSourceBody, indirectSourceBody, notificationIds)
                                .also {
                                    getIAMInfluenceSource(iamInfluenceType, directSourceBody, indirectSourceBody, iamIds, it)
                                } ?: OutcomeSource(null, null)
                            OutcomeEventParams(name, source, weight, timestamp).also {
                                events.add(it)
                            }
                        } catch (e: JSONException) {
                            Logging.error("Generating JSONArray from notifications ids outcome:JSON Failed.", e)
                        }
                    } while (moveToNext())
                }
            }
        } finally {
            cursor?.let {
                if (!it.isClosed) it.close()
            }
        }
        return events
    }

    private fun getNotificationInfluenceSource(
        notificationInfluenceType: InfluenceType,
        directSourceBody: OutcomeSourceBody,
        indirectSourceBody: OutcomeSourceBody,
        notificationIds: String
    ): OutcomeSource? {
        return when (notificationInfluenceType) {
            InfluenceType.DIRECT -> {
                directSourceBody.notificationIds = JSONArray(notificationIds)
                OutcomeSource(directSourceBody, null)
            }
            InfluenceType.INDIRECT -> {
                indirectSourceBody.notificationIds = JSONArray(notificationIds)
                OutcomeSource(null, indirectSourceBody)
            }
            else -> {
                null
            }
        }
    }

    private fun getIAMInfluenceSource(
        iamInfluenceType: InfluenceType,
        directSourceBody: OutcomeSourceBody,
        indirectSourceBody: OutcomeSourceBody,
        iamIds: String,
        source: OutcomeSource?
    ): OutcomeSource? {
        return when (iamInfluenceType) {
            InfluenceType.DIRECT -> {
                directSourceBody.inAppMessagesIds = JSONArray(iamIds)
                source?.setDirectBody(directSourceBody)
                    ?: OutcomeSource(directSourceBody, null)
            }
            InfluenceType.INDIRECT -> {
                indirectSourceBody.inAppMessagesIds = JSONArray(iamIds)
                source?.setIndirectBody(indirectSourceBody)
                    ?: OutcomeSource(null, indirectSourceBody)
            }
            else -> {
                source
            }
        }
    }

    private fun addIdToListFromChannel(cachedUniqueOutcomes: MutableList<CachedUniqueOutcome>, channelIds: JSONArray?, channel: InfluenceChannel) {
        channelIds?.let {
            for (i in 0 until it.length()) {
                try {
                    val influenceId = it.getString(i)
                    cachedUniqueOutcomes.add(CachedUniqueOutcome(influenceId, channel))
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun addIdsToListFromSource(cachedUniqueOutcomes: MutableList<CachedUniqueOutcome>, sourceBody: OutcomeSourceBody?) {
        sourceBody?.let {
            val iamIds = it.inAppMessagesIds
            val notificationIds = it.notificationIds
            addIdToListFromChannel(cachedUniqueOutcomes, iamIds, InfluenceChannel.IAM)
            addIdToListFromChannel(cachedUniqueOutcomes, notificationIds, InfluenceChannel.NOTIFICATION)
        }
    }

    /**
     * Save a JSONArray of notification ids as separate items with the unique outcome name
     */
    @WorkerThread
    @Synchronized
    override fun saveUniqueOutcomeEventParams(eventParams: OutcomeEventParams) {
        Logging.debug("OneSignal saveUniqueOutcomeEventParams: $eventParams")
        val outcomeName = eventParams.outcomeId
        val cachedUniqueOutcomes: MutableList<CachedUniqueOutcome> = ArrayList()
        val directBody = eventParams.outcomeSource?.directBody
        val indirectBody = eventParams.outcomeSource?.indirectBody
        addIdsToListFromSource(cachedUniqueOutcomes, directBody)
        addIdsToListFromSource(cachedUniqueOutcomes, indirectBody)

        for (uniqueOutcome in cachedUniqueOutcomes) {
            ContentValues().apply {
                put(CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID, uniqueOutcome.getInfluenceId())
                put(CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE, uniqueOutcome.getChannel().toString())
                put(CachedUniqueOutcomeTable.COLUMN_NAME_NAME, outcomeName)
            }.also { values ->
                dbHelper.get().insert(CachedUniqueOutcomeTable.TABLE_NAME, null, values)
            }
        }
    }

    /**
     * Create a JSONArray of not cached notification ids from the unique outcome notifications SQL table
     */
    @WorkerThread
    @Synchronized
    override fun getNotCachedUniqueInfluencesForOutcome(name: String, influences: List<Influence>): List<Influence> {
        val uniqueInfluences: MutableList<Influence> = ArrayList()
        var cursor: Cursor? = null
        try {
            for (influence in influences) {
                val availableInfluenceIds = JSONArray()
                val influenceIds = influence.ids ?: continue

                for (i in 0 until influenceIds.length()) {
                    val channelInfluenceId = influenceIds.getString(i)
                    val channel = influence.influenceChannel
                    val columns = arrayOf<String>()
                    val where = CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID + " = ? AND " +
                        CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE + " = ? AND " +
                        CachedUniqueOutcomeTable.COLUMN_NAME_NAME + " = ?"
                    val args = arrayOf(channelInfluenceId, channel.toString(), name)
                    cursor = dbHelper.get().query(
                        CachedUniqueOutcomeTable.TABLE_NAME,
                        columns,
                        where,
                        args,
                        null,
                        null,
                        null,
                        "1"
                    )

                    // Item is not cached, we can use the influence id, add it to the JSONArray
                    if (cursor.count == 0) availableInfluenceIds.put(channelInfluenceId)
                }

                if (availableInfluenceIds.length() > 0) {
                    influence.copy().apply {
                        ids = availableInfluenceIds
                    }.also { newInfluence ->
                        uniqueInfluences.add(newInfluence)
                    }
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        } finally {
            cursor?.let {
                if (!it.isClosed) it.close()
            }
        }
        return uniqueInfluences
    }

    /**
     * Deletes cached unique outcome notifications whose ids do not exist inside of the NotificationTable.TABLE_NAME
     */
    override suspend fun cleanCachedUniqueOutcomeEventNotifications(notificationTableName: String, notificationIdColumnName: String) {
        withContext(Dispatchers.IO) {
            val whereStr = "NOT EXISTS(" +
                "SELECT NULL FROM " + notificationTableName + " n " +
                "WHERE" + " n." + notificationIdColumnName + " = " + OutcomesDbContract.CACHE_UNIQUE_OUTCOME_COLUMN_CHANNEL_INFLUENCE_ID +
                " AND " + OutcomesDbContract.CACHE_UNIQUE_OUTCOME_COLUMN_CHANNEL_TYPE + " = \"" + InfluenceChannel.NOTIFICATION.toString()
                .toLowerCase(Locale.ROOT) +
                "\")"
            dbHelper.get().delete(
                OutcomesDbContract.CACHE_UNIQUE_OUTCOME_TABLE,
                whereStr,
                null
            )
        }
    }
}
