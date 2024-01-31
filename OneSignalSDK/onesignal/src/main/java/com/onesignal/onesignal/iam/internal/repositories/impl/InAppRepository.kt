package com.onesignal.onesignal.iam.internal.repositories.impl

import org.json.JSONException
import android.content.ContentValues
import com.onesignal.onesignal.core.internal.common.JSONUtils
import com.onesignal.onesignal.core.internal.time.ITime
import com.onesignal.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.iam.internal.InAppMessage
import com.onesignal.onesignal.iam.internal.InAppMessageRedisplayStats
import com.onesignal.onesignal.iam.internal.preferences.IInAppPreferencesController
import com.onesignal.onesignal.iam.internal.repositories.IInAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

internal class InAppRepository(
    private val _databaseProvider: IDatabaseProvider,
    private val _time: ITime,
    private val _prefs: IInAppPreferencesController
) : IInAppRepository {

    override suspend fun saveInAppMessage(inAppMessage: InAppMessage) {
        val values = ContentValues()
        values.put(
            OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID,
            inAppMessage.messageId
        )
        values.put(
            OneSignalDbContract.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY,
            inAppMessage.redisplayStats.displayQuantity
        )
        values.put(
            OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY,
            inAppMessage.redisplayStats.lastDisplayTime
        )
        values.put(
            OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS,
            inAppMessage.clickedClickIds.toString()
        )
        values.put(
            OneSignalDbContract.InAppMessageTable.COLUMN_DISPLAYED_IN_SESSION,
            inAppMessage.isDisplayedInSession
        )

        withContext(Dispatchers.IO) {
            val rowsUpdated: Int = _databaseProvider.get().update(
                OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                values,
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID.toString() + " = ?",
                arrayOf<String>(inAppMessage.messageId)
            )

            if (rowsUpdated == 0) {
                _databaseProvider.get().insert(
                    OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                    null,
                    values
                )
            }
        }
    }

    override suspend fun listInAppMessages(): List<InAppMessage> {
        val inAppMessages: MutableList<InAppMessage> = mutableListOf()

        withContext(Dispatchers.IO) {
            try {
                _databaseProvider.get().query(
                    OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                ).use {
                    if (it.moveToFirst()) {
                        do {
                            val messageId =
                                it.getString(it.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID))
                            val clickIds =
                                it.getString(it.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS))
                            val displayQuantity =
                                it.getInt(it.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY))
                            val lastDisplay =
                                it.getLong(it.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY))
                            val displayed =
                                it.getInt(it.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_DISPLAYED_IN_SESSION)) == 1

                            val clickIdsSet: Set<String> =
                                JSONUtils.newStringSetFromJSONArray(JSONArray(clickIds))
                            val inAppMessage = InAppMessage(
                                messageId,
                                clickIdsSet,
                                displayed,
                                InAppMessageRedisplayStats(displayQuantity, lastDisplay, _time),
                                _time
                            )
                            inAppMessages.add(inAppMessage)
                        } while (it.moveToNext())
                    }
                }
            } catch (e: JSONException) {
                Logging.error("Generating JSONArray from iam click ids:JSON Failed.", e)
            }
        }

        return inAppMessages
    }

    override suspend fun cleanCachedInAppMessages() {
        // 1. Query for all old message ids and old clicked click ids
        val retColumns = arrayOf<String>(
            OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID,
            OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS
        )
        val whereStr: String =
            OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY.toString() + " < ?"
        val sixMonthsAgoInSeconds =
            (System.currentTimeMillis() / 1000L - IAM_CACHE_DATA_LIFETIME).toString()
        val whereArgs = arrayOf(sixMonthsAgoInSeconds)
        val oldMessageIds: MutableSet<String> = mutableSetOf()
        val oldClickedClickIds: MutableSet<String> = mutableSetOf()

        try {
            _databaseProvider.get().query(
                OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                retColumns,
                whereStr,
                whereArgs,
                null,
                null,
                null
            ).use {
                if (it.count == 0) {
                    Logging.debug("Attempted to clean 6 month old IAM data, but none exists!")
                    return
                }

                // From cursor get all of the old message ids and old clicked click ids
                if (it.moveToFirst()) {
                    do {
                        val oldMessageId = it.getString(it.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID))
                        val oldClickIds = it.getString(it.getColumnIndex(OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS))

                        oldMessageIds.add(oldMessageId)
                        oldClickedClickIds.addAll(JSONUtils.newStringSetFromJSONArray(JSONArray(oldClickIds)))
                    } while (it.moveToNext())
                }
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        // 2. Delete old IAMs from SQL
        _databaseProvider.get().delete(
            OneSignalDbContract.InAppMessageTable.TABLE_NAME,
            whereStr,
            whereArgs
        )

        // 3. Use queried data to clean SharedPreferences
        _prefs.cleanInAppMessageIds(oldMessageIds)
        _prefs.cleanInAppMessageClickedClickIds(oldClickedClickIds)
    }

    companion object {
        const val IAM_CACHE_DATA_LIFETIME = 15552000L // 6 months in seconds
    }
}