/**
 * Modified MIT License
 *
 * Copyright 2017 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.onesignal.onesignal.notification.internal.actions

import android.content.Intent
import android.app.Activity
import org.json.JSONException
import android.content.ContentValues
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.onesignal.onesignal.core.internal.common.JSONUtils
import com.onesignal.onesignal.core.internal.database.IDatabaseProvider
import com.onesignal.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.onesignal.notification.internal.NotificationHelper
import com.onesignal.onesignal.notification.internal.data.INotificationDataController
import com.onesignal.onesignal.notification.internal.data.NotificationSummaryManager
import com.onesignal.onesignal.core.internal.params.IParamsService
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.notification.internal.badges.BadgeCountUpdater
import com.onesignal.onesignal.notification.internal.generation.GenerateNotification
import org.json.JSONArray
import org.json.JSONObject

// Process both notifications opens and dismisses.
internal class NotificationOpenedProcessor(
    private val _databaseProvider: IDatabaseProvider,
    private val _summaryManager: NotificationSummaryManager,
    private val _dataController: INotificationDataController,
    private val _paramsService: IParamsService,
    private val _badgeUpdater: BadgeCountUpdater
) {
    private val TAG = NotificationOpenedProcessor::class.java.canonicalName
    suspend fun processFromContext(context: Context, intent: Intent) {
        if (!isOneSignalIntent(intent))
            return

        handleDismissFromActionButtonPress(context, intent)
        processIntent(context, intent)
    }

    // Was Bundle created from our SDK? Prevents external Intents
    // TODO: Could most likely be simplified checking if BUNDLE_KEY_ONESIGNAL_DATA is present
    private fun isOneSignalIntent(intent: Intent): Boolean {
        return intent.hasExtra(GenerateNotification.BUNDLE_KEY_ONESIGNAL_DATA) || intent.hasExtra("summary") || intent.hasExtra(
            GenerateNotification.BUNDLE_KEY_ANDROID_NOTIFICATION_ID
        )
    }

    private fun handleDismissFromActionButtonPress(context: Context?, intent: Intent) {
        // Pressed an action button, need to clear the notification and close the notification area manually.
        if (intent.getBooleanExtra("action_button", false)) {
            NotificationManagerCompat.from(context!!).cancel(
                intent.getIntExtra(
                    GenerateNotification.BUNDLE_KEY_ANDROID_NOTIFICATION_ID,
                    0
                )
            )

            // Only close the notifications shade on Android versions where it is allowed, Android 11 and lower.
            // See https://developer.android.com/about/versions/12/behavior-changes-all#close-system-dialogs
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                context.sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
            }
        }
    }

    suspend fun processIntent(context: Context, intent: Intent) {
        val summaryGroup = intent.getStringExtra("summary")
        val dismissed = intent.getBooleanExtra("dismissed", false)
        var intentExtras: NotificationIntentExtras? = null
        if (!dismissed) {
            intentExtras = processToOpenIntent(context, intent, summaryGroup)
            if (intentExtras == null) return
        }
        markNotificationsConsumed(context, intent, dismissed)

        // Notification is not a summary type but a single notification part of a group.
        if (summaryGroup == null) {
            val group = intent.getStringExtra("grp")
            if (group != null)
                _summaryManager.updateSummaryNotificationAfterChildRemoved(context, group, dismissed)
        }
        Logging.debug("processIntent from context: $context and intent: $intent")
        if (intent.extras != null)
            Logging.debug("""processIntent intent extras: ${intent.extras.toString()}""")
        if (!dismissed) {
            if (context !is Activity)
                Logging.error("NotificationOpenedProcessor processIntent from an non Activity context: $context")
            else {
                // TODO: Implement
                //OneSignal.handleNotificationOpen(context as Activity?, intentExtras!!.dataArray, NotificationFormatHelper.getOSNotificationIdFromJson(intentExtras.jsonData))
            }
        }
    }

    fun processToOpenIntent(
        context: Context?,
        intent: Intent,
        summaryGroup: String?
    ): NotificationIntentExtras? {
        var dataArray: JSONArray? = null
        var jsonData: JSONObject? = null
        try {
            jsonData =
                JSONObject(intent.getStringExtra(GenerateNotification.BUNDLE_KEY_ONESIGNAL_DATA))

            if (context !is Activity)
                Logging.error("NotificationOpenedProcessor processIntent from an non Activity context: $context")
            // TODO: Implement
            //else if (OSInAppMessagePreviewHandler.notificationOpened((context as Activity?)!!, jsonData))
            //    return null

            jsonData.put(
                GenerateNotification.BUNDLE_KEY_ANDROID_NOTIFICATION_ID, intent.getIntExtra(
                    GenerateNotification.BUNDLE_KEY_ANDROID_NOTIFICATION_ID, 0
                )
            )
            intent.putExtra(GenerateNotification.BUNDLE_KEY_ONESIGNAL_DATA, jsonData.toString())
            dataArray = JSONUtils.wrapInJsonArray(
                JSONObject(
                    intent.getStringExtra(
                        GenerateNotification.BUNDLE_KEY_ONESIGNAL_DATA
                    )
                )
            )
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        // We just opened a summary notification.
        if (summaryGroup != null)
            addChildNotifications(dataArray, summaryGroup)

        return NotificationIntentExtras(dataArray, jsonData)
    }

    private fun addChildNotifications(dataArray: JSONArray?, summaryGroup: String) {

        val retColumn = arrayOf(OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA)
        val whereArgs = arrayOf(summaryGroup)
        val cursor = _databaseProvider.get().query(
            OneSignalDbContract.NotificationTable.TABLE_NAME,
            retColumn,
            OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " = ? AND " +  // Where String
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 AND " +
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_IS_SUMMARY + " = 0",
            whereArgs,
            null, null, null
        )
        if (cursor.count > 1) {
            cursor.moveToFirst()
            do {
                try {
                    val jsonStr: String =
                        cursor.getString(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA))
                    dataArray!!.put(JSONObject(jsonStr))
                } catch (e: JSONException) {
                    Logging.error("Could not parse JSON of sub notification in group: $summaryGroup")
                }
            } while (cursor.moveToNext())
        }
        cursor.close()
    }

    private suspend fun markNotificationsConsumed(
        context: Context,
        intent: Intent,
        dismissed: Boolean
    ) {
        val summaryGroup = intent.getStringExtra("summary")
        var whereStr: String
        var whereArgs: Array<String>? = null
        if (summaryGroup != null) {
            val isGroupless = summaryGroup == NotificationHelper.grouplessSummaryKey
            if (isGroupless)
                whereStr = OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " IS NULL"
            else {
                whereStr = OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID + " = ?"
                whereArgs = arrayOf(summaryGroup)
            }
            if (!dismissed) {
                // Make sure when a notification is not being dismissed it is handled through the dashboard setting
                if (!_paramsService.clearGroupOnSummaryClick) {
                    /* If the open event shouldn't clear all summary notifications then the SQL query
                * will look for the most recent notification instead of all grouped notifications */
                    val mostRecentId = _dataController.getMostRecentNotifIdFromGroup(summaryGroup, isGroupless).toString()
                    whereStr += " AND " + OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = ?"
                    whereArgs = if (isGroupless) arrayOf(mostRecentId) else arrayOf(
                        summaryGroup,
                        mostRecentId
                    )
                }
            }
        } else
            whereStr = OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + intent.getIntExtra(
                    GenerateNotification.BUNDLE_KEY_ANDROID_NOTIFICATION_ID, 0
                )

        clearStatusBarNotifications(context, summaryGroup)
        _databaseProvider.get().update(
            OneSignalDbContract.NotificationTable.TABLE_NAME,
            newContentValuesWithConsumed(intent),
            whereStr,
            whereArgs
        )

        _badgeUpdater.update(context)
    }

    /**
     * Handles clearing the status bar notifications when opened
     */
    private suspend fun clearStatusBarNotifications(context: Context, summaryGroup: String?) {
        // Handling for clearing the notification when opened
        if (summaryGroup != null)
            _summaryManager.clearNotificationOnSummaryClick(context, summaryGroup)
        else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // The summary group is null, represents the last notification in the groupless group
                // Check that no more groupless notifications exist in the group and cancel the group
                val grouplessCount = NotificationHelper.getGrouplessNotifsCount(context)
                if (grouplessCount < 1) {
                    val groupId = NotificationHelper.grouplessSummaryId
                    val notificationManager =
                        NotificationHelper.getNotificationManager(context)
                    notificationManager.cancel(groupId)
                }
            }
        }
    }

    private fun newContentValuesWithConsumed(intent: Intent): ContentValues {
        val values = ContentValues()
        val dismissed = intent.getBooleanExtra("dismissed", false)
        if (dismissed) values.put(
            OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED,
            1
        ) else values.put(
            OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED, 1
        )
        return values
    }
}