package com.onesignal.session.internal.outcomes.impl

import android.content.ContentValues
import com.onesignal.common.threading.CoroutineDispatcherProvider
import com.onesignal.common.threading.DefaultDispatcherProvider
import com.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.session.internal.influence.Influence
import com.onesignal.session.internal.influence.InfluenceChannel
import com.onesignal.session.internal.influence.InfluenceType
import com.onesignal.session.internal.influence.InfluenceType.Companion.fromString
import com.onesignal.session.internal.outcomes.migrations.RemoveInvalidSessionTimeRecords
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.util.Locale

internal class OutcomeEventsRepository(
    private val _databaseProvider: IDatabaseProvider,
    private val dispatchers: CoroutineDispatcherProvider = DefaultDispatcherProvider(),
) : IOutcomeEventsRepository {
    /**
     * Delete event from the DB
     */
    override suspend fun deleteOldOutcomeEvent(event: OutcomeEventParams) {
        withContext(dispatchers.io) {
            _databaseProvider.os.delete(
                OutcomeEventsTable.TABLE_NAME,
                OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + " = ?",
                arrayOf(event.timestamp.toString()),
            )
        }
    }

    /**
     * Save an outcome event to send it on the future
     * For offline mode and contingency of errors
     */
    override suspend fun saveOutcomeEvent(eventParams: OutcomeEventParams) {
        withContext(dispatchers.io) {
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
                put(
                    OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE,
                    notificationInfluenceType.toString().lowercase(Locale.ROOT),
                )
                put(
                    OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE,
                    iamInfluenceType.toString().lowercase(Locale.ROOT),
                )
                // Save outcome data
                put(OutcomeEventsTable.COLUMN_NAME_NAME, eventParams.outcomeId)
                put(OutcomeEventsTable.COLUMN_NAME_WEIGHT, eventParams.weight)
                put(OutcomeEventsTable.COLUMN_NAME_TIMESTAMP, eventParams.timestamp)
                put(OutcomeEventsTable.COLUMN_NAME_SESSION_TIME, eventParams.sessionTime)
            }.also { values ->
                _databaseProvider.os.insert(OutcomeEventsTable.TABLE_NAME, null, values)
            }
        }
    }

    /**
     * Save an outcome event to send it on the future
     * For offline mode and contingency of errors
     */
    override suspend fun getAllEventsToSend(): List<OutcomeEventParams> {
        val events: MutableList<OutcomeEventParams> = ArrayList()
        withContext(dispatchers.io) {
            RemoveInvalidSessionTimeRecords.run(_databaseProvider)
            _databaseProvider.os.query(OutcomeEventsTable.TABLE_NAME) { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        // Retrieve influence types
                        val notificationInfluenceTypeString =
                            cursor.getString(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_INFLUENCE_TYPE)
                        val notificationInfluenceType =
                            fromString(notificationInfluenceTypeString)
                        val iamInfluenceTypeString =
                            cursor.getString(OutcomeEventsTable.COLUMN_NAME_IAM_INFLUENCE_TYPE)
                        val iamInfluenceType = fromString(iamInfluenceTypeString)

                        // Retrieve influence ids
                        val notificationIds =
                            cursor.getOptString(OutcomeEventsTable.COLUMN_NAME_NOTIFICATION_IDS)
                                ?: "[]"
                        val iamIds =
                            cursor.getOptString(OutcomeEventsTable.COLUMN_NAME_IAM_IDS)
                                ?: "[]"

                        // Retrieve Outcome data
                        val name =
                            cursor.getString(OutcomeEventsTable.COLUMN_NAME_NAME)
                        val weight =
                            cursor.getFloat(OutcomeEventsTable.COLUMN_NAME_WEIGHT)
                        val timestamp =
                            cursor.getLong(OutcomeEventsTable.COLUMN_NAME_TIMESTAMP)
                        val sessionTime =
                            cursor.getLong(OutcomeEventsTable.COLUMN_NAME_SESSION_TIME)

                        try {
                            val directSourceBody = OutcomeSourceBody()
                            val indirectSourceBody = OutcomeSourceBody()
                            val source: OutcomeSource =
                                getNotificationInfluenceSource(
                                    notificationInfluenceType,
                                    directSourceBody,
                                    indirectSourceBody,
                                    notificationIds,
                                )
                                    .also {
                                        getIAMInfluenceSource(
                                            iamInfluenceType,
                                            directSourceBody,
                                            indirectSourceBody,
                                            iamIds,
                                            it,
                                        )
                                    } ?: OutcomeSource(null, null)
                            OutcomeEventParams(name, source, weight, sessionTime, timestamp).also {
                                events.add(it)
                            }
                        } catch (e: JSONException) {
                            Logging.error(
                                "Generating JSONArray from notifications ids outcome:JSON Failed.",
                                e,
                            )
                        }
                    } while (cursor.moveToNext())
                }
            }
        }
        return events
    }

    private fun getNotificationInfluenceSource(
        notificationInfluenceType: InfluenceType,
        directSourceBody: OutcomeSourceBody,
        indirectSourceBody: OutcomeSourceBody,
        notificationIds: String,
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
        source: OutcomeSource?,
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

    private fun addIdToListFromChannel(
        cachedUniqueOutcomes: MutableList<CachedUniqueOutcome>,
        channelIds: JSONArray?,
        channel: InfluenceChannel,
    ) {
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

    private fun addIdsToListFromSource(
        cachedUniqueOutcomes: MutableList<CachedUniqueOutcome>,
        sourceBody: OutcomeSourceBody?,
    ) {
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
    override suspend fun saveUniqueOutcomeEventParams(eventParams: OutcomeEventParams) {
        Logging.debug("OutcomeEventsCache.saveUniqueOutcomeEventParams(eventParams: $eventParams)")

        withContext(dispatchers.io) {
            val outcomeName = eventParams.outcomeId
            val cachedUniqueOutcomes: MutableList<CachedUniqueOutcome> = ArrayList()
            val directBody = eventParams.outcomeSource?.directBody
            val indirectBody = eventParams.outcomeSource?.indirectBody
            addIdsToListFromSource(cachedUniqueOutcomes, directBody)
            addIdsToListFromSource(cachedUniqueOutcomes, indirectBody)

            for (uniqueOutcome in cachedUniqueOutcomes) {
                ContentValues().apply {
                    put(
                        CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID,
                        uniqueOutcome.influenceId,
                    )
                    put(
                        CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE,
                        uniqueOutcome.channel.toString(),
                    )
                    put(CachedUniqueOutcomeTable.COLUMN_NAME_NAME, outcomeName)
                }.also { values ->
                    _databaseProvider.os.insert(CachedUniqueOutcomeTable.TABLE_NAME, null, values)
                }
            }
        }
    }

    /**
     * Create a JSONArray of not cached notification ids from the unique outcome notifications SQL table
     */
    override suspend fun getNotCachedUniqueInfluencesForOutcome(
        name: String,
        influences: List<Influence>,
    ): List<Influence> {
        val uniqueInfluences: MutableList<Influence> = ArrayList()

        withContext(dispatchers.io) {
            try {
                for (influence in influences) {
                    val availableInfluenceIds = JSONArray()
                    val influenceIds = influence.ids ?: continue

                    for (i in 0 until influenceIds.length()) {
                        val channelInfluenceId = influenceIds.getString(i)
                        val channel = influence.influenceChannel
                        val columns = arrayOf<String>()
                        val where =
                            CachedUniqueOutcomeTable.COLUMN_CHANNEL_INFLUENCE_ID + " = ? AND " +
                                CachedUniqueOutcomeTable.COLUMN_CHANNEL_TYPE + " = ? AND " +
                                CachedUniqueOutcomeTable.COLUMN_NAME_NAME + " = ?"
                        val args = arrayOf(channelInfluenceId, channel.toString(), name)
                        _databaseProvider.os.query(
                            CachedUniqueOutcomeTable.TABLE_NAME,
                            columns = columns,
                            whereClause = where,
                            whereArgs = args,
                            limit = "1",
                        ) {
                            // Item is not cached, we can use the influence id, add it to the JSONArray
                            if (it.count == 0) availableInfluenceIds.put(channelInfluenceId)
                        }
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
            }
        }

        return uniqueInfluences
    }

    /**
     * Deletes cached unique outcome notifications whose ids do not exist inside of the NotificationTable.TABLE_NAME
     */
    override suspend fun cleanCachedUniqueOutcomeEventNotifications() {
        val notificationTableName = OneSignalDbContract.NotificationTable.TABLE_NAME
        val notificationIdColumnName = OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID

        withContext(dispatchers.io) {
            val whereStr =
                "NOT EXISTS(" +
                    "SELECT NULL FROM " + notificationTableName + " n " +
                    "WHERE" + " n." + notificationIdColumnName + " = " + OutcomesDbContract.CACHE_UNIQUE_OUTCOME_COLUMN_CHANNEL_INFLUENCE_ID +
                    " AND " + OutcomesDbContract.CACHE_UNIQUE_OUTCOME_COLUMN_CHANNEL_TYPE + " = \"" +
                    InfluenceChannel.NOTIFICATION.toString()
                        .lowercase(Locale.ROOT) +
                    "\")"
            _databaseProvider.os.delete(
                OutcomesDbContract.CACHE_UNIQUE_OUTCOME_TABLE,
                whereStr,
                null,
            )
        }
    }
}
