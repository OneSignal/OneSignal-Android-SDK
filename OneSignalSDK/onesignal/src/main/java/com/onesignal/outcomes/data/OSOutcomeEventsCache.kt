package com.onesignal.outcomes.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.annotation.WorkerThread
import com.onesignal.OSLogger
import com.onesignal.OSSharedPreferences
import com.onesignal.OneSignalDb
import com.onesignal.influence.domain.OSInfluence
import com.onesignal.influence.domain.OSInfluenceChannel
import com.onesignal.influence.domain.OSInfluenceType
import com.onesignal.influence.domain.OSInfluenceType.Companion.fromString
import com.onesignal.outcomes.OSOutcomeConstants
import com.onesignal.outcomes.domain.OSCachedUniqueOutcome
import com.onesignal.outcomes.domain.OSOutcomeEventParams
import com.onesignal.outcomes.domain.OSOutcomeSource
import com.onesignal.outcomes.domain.OSOutcomeSourceBody
import org.json.JSONArray
import org.json.JSONException
import java.util.*

internal class OSOutcomeEventsCache(private val logger: OSLogger,
                                    private val dbHelper: OneSignalDb,
                                    private val preferences: OSSharedPreferences) {
    val isOutcomesV2ServiceEnabled: Boolean
        get() = preferences.getBool(
                preferences.preferencesName,
                preferences.outcomesV2KeyName,
                false)

    val unattributedUniqueOutcomeEventsSentByChannel: Set<String>?
        get() = preferences.getStringSet(
                preferences.preferencesName,
                OSOutcomeConstants.PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT,
                null)

    fun saveUnattributedUniqueOutcomeEventsSentByChannel(unattributedUniqueOutcomeEvents: Set<String?>?) {
        preferences.saveStringSet(
                preferences.preferencesName,
                OSOutcomeConstants.PREFS_OS_UNATTRIBUTED_UNIQUE_OUTCOME_EVENTS_SENT,  // Post success, store unattributed unique outcome event names
                unattributedUniqueOutcomeEvents!!)
    }

    /**
     * Delete event from the DB
     */
    @WorkerThread
    @Synchronized
    fun deleteOldOutcomeEvent(event: OSOutcomeEventParams) {
        dbHelper.delete(OutcomeEventsTable.TABLE_NAME, OutcomeEventsTable.COLUMN_NAME_TIMESTAMP + " = ?", arrayOf(event.timestamp.toString()))
    }

    /**
     * Save an outcome event to send it on the future
     * For offline mode and contingency of errors
     */
    @WorkerThread
    @Synchronized
    fun saveOutcomeEvent(eventParams: OSOutcomeEventParams) {
        var notificationIds = JSONArray()
        var iamIds = JSONArray()
        var notificationInfluenceType = OSInfluenceType.UNATTRIBUTED
        var iamInfluenceType = OSInfluenceType.UNATTRIBUTED
        // Check for direct channels
        eventParams.outcomeSource?.directBody?.let { directBody ->
            directBody.notificationIds?.let {
                if (it.length() > 0) {
                    notificationInfluenceType = OSInfluenceType.DIRECT
                    notificationIds = it
                }
            }
            directBody.inAppMessagesIds?.let {
                if (it.length() > 0) {
                    iamInfluenceType = OSInfluenceType.DIRECT
                    iamIds = it
                }
            }
        }
        // Check for indirect channels
        eventParams.outcomeSource?.indirectBody?.let { indirectBody ->
            indirectBody.notificationIds?.let {
                if (it.length() > 0) {
                    notificationInfluenceType = OSInfluenceType.INDIRECT
                    notificationIds = it
                }
            }
            indirectBody.inAppMessagesIds?.let {
                if (it.length() > 0) {
                    iamInfluenceType = OSInfluenceType.INDIRECT
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
            dbHelper.insert(OutcomeEventsTable.TABLE_NAME, null, values)
        }
    }

    /**
     * Save an outcome event to send it on the future
     * For offline mode and contingency of errors
     */
    @WorkerThread
    @Synchronized
    fun getAllEventsToSend(): List<OSOutcomeEventParams> {
        val events: MutableList<OSOutcomeEventParams> = ArrayList()
        var cursor: Cursor? = null
        try {
            cursor = dbHelper.query(
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
                            val directSourceBody = OSOutcomeSourceBody()
                            val indirectSourceBody = OSOutcomeSourceBody()
                            val source: OSOutcomeSource = getNotificationInfluenceSource(notificationInfluenceType, directSourceBody, indirectSourceBody, notificationIds)
                                    .also {
                                        getIAMInfluenceSource(iamInfluenceType, directSourceBody, indirectSourceBody, iamIds, it)
                                    } ?: OSOutcomeSource(null, null)
                            OSOutcomeEventParams(name, source, weight, timestamp).also {
                                events.add(it)
                            }
                        } catch (e: JSONException) {
                            logger.error("Generating JSONArray from notifications ids outcome:JSON Failed.", e)
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

    private fun getNotificationInfluenceSource(notificationInfluenceType: OSInfluenceType,
                                               directSourceBody: OSOutcomeSourceBody,
                                               indirectSourceBody: OSOutcomeSourceBody,
                                               notificationIds: String): OSOutcomeSource? {
        return when (notificationInfluenceType) {
            OSInfluenceType.DIRECT -> {
                directSourceBody.notificationIds = JSONArray(notificationIds)
                OSOutcomeSource(directSourceBody, null)
            }
            OSInfluenceType.INDIRECT -> {
                indirectSourceBody.notificationIds = JSONArray(notificationIds)
                OSOutcomeSource(null, indirectSourceBody)
            }
            else -> {
                null
            }
        }
    }

    private fun getIAMInfluenceSource(iamInfluenceType: OSInfluenceType,
                                      directSourceBody: OSOutcomeSourceBody,
                                      indirectSourceBody: OSOutcomeSourceBody,
                                      iamIds: String,
                                      source: OSOutcomeSource?): OSOutcomeSource? {
        return when (iamInfluenceType) {
            OSInfluenceType.DIRECT -> {
                directSourceBody.inAppMessagesIds = JSONArray(iamIds)
                source?.setDirectBody(directSourceBody)
                        ?: OSOutcomeSource(directSourceBody, null)
            }
            OSInfluenceType.INDIRECT -> {
                indirectSourceBody.inAppMessagesIds = JSONArray(iamIds)
                source?.setIndirectBody(indirectSourceBody)
                        ?: OSOutcomeSource(null, indirectSourceBody)
            }
            else -> {
                source
            }
        }
    }

    private fun addIdToListFromChannel(cachedUniqueOutcomes: MutableList<OSCachedUniqueOutcome>, channelIds: JSONArray?, channel: OSInfluenceChannel) {
        channelIds?.let {
            for (i in 0 until it.length()) {
                try {
                    val influenceId = it.getString(i)
                    cachedUniqueOutcomes.add(OSCachedUniqueOutcome(influenceId, channel))
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun addIdsToListFromSource(cachedUniqueOutcomes: MutableList<OSCachedUniqueOutcome>, sourceBody: OSOutcomeSourceBody?) {
        sourceBody?.let {
            val iamIds = it.inAppMessagesIds
            val notificationIds = it.notificationIds
            addIdToListFromChannel(cachedUniqueOutcomes, iamIds, OSInfluenceChannel.IAM)
            addIdToListFromChannel(cachedUniqueOutcomes, notificationIds, OSInfluenceChannel.NOTIFICATION)
        }
    }

    /**
     * Save a JSONArray of notification ids as separate items with the unique outcome name
     */
    @WorkerThread
    @Synchronized
    fun saveUniqueOutcomeEventParams(eventParams: OSOutcomeEventParams) {
        logger.debug("OneSignal saveUniqueOutcomeEventParams: $eventParams")
        val outcomeName = eventParams.outcomeId
        val cachedUniqueOutcomes: MutableList<OSCachedUniqueOutcome> = ArrayList()
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
                dbHelper.insert(CachedUniqueOutcomeTable.TABLE_NAME, null, values)
            }
        }
    }

    /**
     * Create a JSONArray of not cached notification ids from the unique outcome notifications SQL table
     */
    @WorkerThread
    @Synchronized
    fun getNotCachedUniqueInfluencesForOutcome(name: String, influences: List<OSInfluence>): List<OSInfluence> {
        val uniqueInfluences: MutableList<OSInfluence> = ArrayList()
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
                    cursor = dbHelper.query(
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
    @WorkerThread
    @Synchronized
    fun cleanCachedUniqueOutcomeEventNotifications(notificationTableName: String, notificationIdColumnName: String) {
        val whereStr = "NOT EXISTS(" +
                "SELECT NULL FROM " + notificationTableName + " n " +
                "WHERE" + " n." + notificationIdColumnName + " = " + OutcomesDbContract.CACHE_UNIQUE_OUTCOME_COLUMN_CHANNEL_INFLUENCE_ID +
                " AND " + OutcomesDbContract.CACHE_UNIQUE_OUTCOME_COLUMN_CHANNEL_TYPE + " = \"" + OSInfluenceChannel.NOTIFICATION.toString().toLowerCase(Locale.ROOT) +
                "\")"
        dbHelper.delete(
                OutcomesDbContract.CACHE_UNIQUE_OUTCOME_TABLE,
                whereStr,
                null)
    }
}