package com.onesignal.inAppMessages.internal.repositories.impl

import android.content.ContentValues
import com.onesignal.common.JSONUtils
import com.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.core.internal.time.ITime
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.inAppMessages.internal.InAppMessage
import com.onesignal.inAppMessages.internal.InAppMessageRedisplayStats
import com.onesignal.inAppMessages.internal.preferences.IInAppPreferencesController
import com.onesignal.inAppMessages.internal.repositories.IInAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

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
            val rowsUpdated: Int = _databaseProvider.os.update(
                OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                values,
                OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID.toString() + " = ?",
                arrayOf<String>(inAppMessage.messageId)
            )

            if (rowsUpdated == 0) {
                _databaseProvider.os.insert(
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
                _databaseProvider.os.query(OneSignalDbContract.InAppMessageTable.TABLE_NAME) {
                    if (it.moveToFirst()) {
                        do {
                            val messageId =
                                it.getString(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID)
                            val clickIds =
                                it.getString(OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS)
                            val displayQuantity =
                                it.getInt(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_DISPLAY_QUANTITY)
                            val lastDisplay =
                                it.getLong(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_LAST_DISPLAY)
                            val displayed =
                                it.getInt(OneSignalDbContract.InAppMessageTable.COLUMN_DISPLAYED_IN_SESSION) == 1

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
        withContext(Dispatchers.IO) {
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
                _databaseProvider.os.query(
                    OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                    columns = retColumns,
                    whereClause = whereStr,
                    whereArgs = whereArgs
                ) {
                    if (it.count == 0) {
                        Logging.debug("Attempted to clean 6 month old IAM data, but none exists!")
                        return@query
                    }

                    // From cursor get all of the old message ids and old clicked click ids
                    if (it.moveToFirst()) {
                        do {
                            val oldMessageId =
                                it.getString(OneSignalDbContract.InAppMessageTable.COLUMN_NAME_MESSAGE_ID)
                            val oldClickIds =
                                it.getString(OneSignalDbContract.InAppMessageTable.COLUMN_CLICK_IDS)

                            oldMessageIds.add(oldMessageId)
                            oldClickedClickIds.addAll(
                                JSONUtils.newStringSetFromJSONArray(
                                    JSONArray(
                                        oldClickIds
                                    )
                                )
                            )
                        } while (it.moveToNext())
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }

            // 2. Delete old IAMs from SQL
            _databaseProvider.os.delete(
                OneSignalDbContract.InAppMessageTable.TABLE_NAME,
                whereStr,
                whereArgs
            )

            // 3. Use queried data to clean SharedPreferences
            _prefs.cleanInAppMessageIds(oldMessageIds)
            _prefs.cleanInAppMessageClickedClickIds(oldClickedClickIds)
        }
    }

    companion object {
        const val IAM_CACHE_DATA_LIFETIME = 15552000L // 6 months in seconds
    }
}
