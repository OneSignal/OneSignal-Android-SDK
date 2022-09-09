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
package com.onesignal.notification.internal.open.impl

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.onesignal.core.internal.common.JSONUtils
import com.onesignal.core.internal.database.impl.OneSignalDbContract
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.params.IParamsService
import com.onesignal.notification.internal.common.NotificationConstants
import com.onesignal.notification.internal.common.NotificationFormatHelper
import com.onesignal.notification.internal.common.NotificationHelper
import com.onesignal.notification.internal.data.INotificationDataController
import com.onesignal.notification.internal.lifecycle.INotificationLifecycleService
import com.onesignal.notification.internal.open.INotificationOpenedProcessor
import com.onesignal.notification.internal.summary.INotificationSummaryManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class NotificationOpenedProcessor(
    private val _summaryManager: INotificationSummaryManager,
    private val _dataController: INotificationDataController,
    private val _paramsService: IParamsService,
    private val _lifecycleService: INotificationLifecycleService
) : INotificationOpenedProcessor {

    override suspend fun processFromContext(context: Context, intent: Intent) {
        if (!isOneSignalIntent(intent)) {
            return
        }

        handleDismissFromActionButtonPress(context, intent)
        processIntent(context, intent)
    }

    // Was Bundle created from our SDK? Prevents external Intents
    // TODO: Could most likely be simplified checking if BUNDLE_KEY_ONESIGNAL_DATA is present
    private fun isOneSignalIntent(intent: Intent): Boolean {
        return intent.hasExtra(NotificationConstants.BUNDLE_KEY_ONESIGNAL_DATA) || intent.hasExtra("summary") || intent.hasExtra(
            NotificationConstants.BUNDLE_KEY_ANDROID_NOTIFICATION_ID
        )
    }

    @SuppressLint("MissingPermission")
    private fun handleDismissFromActionButtonPress(context: Context?, intent: Intent) {
        // Pressed an action button, need to clear the notification and close the notification area manually.
        if (intent.getBooleanExtra("action_button", false)) {
            NotificationManagerCompat.from(context!!).cancel(
                intent.getIntExtra(
                    NotificationConstants.BUNDLE_KEY_ANDROID_NOTIFICATION_ID,
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

    private suspend fun processIntent(context: Context, intent: Intent) {
        val summaryGroup = intent.getStringExtra("summary")
        val dismissed = intent.getBooleanExtra("dismissed", false)
        var intentExtras: NotificationIntentExtras? = null
        if (!dismissed) {
            intentExtras = processToOpenIntent(context, intent, summaryGroup)
            if (intentExtras == null) {
                return
            }
        }

        markNotificationsConsumed(context, intent, dismissed)

        // Notification is not a summary type but a single notification part of a group.
        if (summaryGroup == null) {
            val group = intent.getStringExtra("grp")
            if (group != null) {
                _summaryManager.updateSummaryNotificationAfterChildRemoved(group, dismissed)
            }
        }
        Logging.debug("processIntent from context: $context and intent: $intent")
        if (intent.extras != null) {
            Logging.debug("""processIntent intent extras: ${intent.extras}""")
        }
        if (!dismissed) {
            if (context !is Activity) {
                Logging.error("NotificationOpenedProcessor processIntent from an non Activity context: $context")
            } else {
                _lifecycleService.notificationOpened(context, intentExtras!!.dataArray, NotificationFormatHelper.getOSNotificationIdFromJson(intentExtras.jsonData)!!)
            }
        }
    }

    private suspend fun processToOpenIntent(
        context: Context?,
        intent: Intent,
        summaryGroup: String?
    ): NotificationIntentExtras? {
        var dataArray: JSONArray? = null
        var jsonData: JSONObject? = null
        try {
            jsonData = JSONObject(intent.getStringExtra(NotificationConstants.BUNDLE_KEY_ONESIGNAL_DATA))

            if (context !is Activity) {
                Logging.error("NotificationOpenedProcessor processIntent from an non Activity context: $context")
            } else if (!_lifecycleService.canOpenNotification(context, jsonData)) {
                return null
            }

            jsonData.put(
                NotificationConstants.BUNDLE_KEY_ANDROID_NOTIFICATION_ID,
                intent.getIntExtra(NotificationConstants.BUNDLE_KEY_ANDROID_NOTIFICATION_ID, 0)
            )
            intent.putExtra(NotificationConstants.BUNDLE_KEY_ONESIGNAL_DATA, jsonData.toString())
            dataArray = JSONUtils.wrapInJsonArray(
                JSONObject(intent.getStringExtra(NotificationConstants.BUNDLE_KEY_ONESIGNAL_DATA))
            )
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        // We just opened a summary notification.
        if (summaryGroup != null) {
            addChildNotifications(dataArray!!, summaryGroup)
        }

        return NotificationIntentExtras(dataArray!!, jsonData!!)
    }

    private suspend fun addChildNotifications(dataArray: JSONArray, summaryGroup: String) {
        val childNotifications = _dataController.listNotificationsForGroup(summaryGroup)
        for (childNotification in childNotifications)
            dataArray!!.put(JSONObject(childNotification.fullData))
    }

    private suspend fun markNotificationsConsumed(
        context: Context,
        intent: Intent,
        dismissed: Boolean
    ) {
        val summaryGroup = intent.getStringExtra("summary")

        clearStatusBarNotifications(context, summaryGroup)
        _dataController.markAsConsumed(
            intent.getIntExtra(NotificationConstants.BUNDLE_KEY_ANDROID_NOTIFICATION_ID, 0),
            dismissed,
            summaryGroup,
            _paramsService.clearGroupOnSummaryClick
        )
    }

    /**
     * Handles clearing the status bar notifications when opened
     */
    private suspend fun clearStatusBarNotifications(context: Context, summaryGroup: String?) {
        // Handling for clearing the notification when opened
        if (summaryGroup != null) {
            _summaryManager.clearNotificationOnSummaryClick(summaryGroup)
        } else {
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
        if (dismissed) {
            values.put(
                OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED,
                1
            )
        } else {
            values.put(
                OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED,
                1
            )
        }
        return values
    }
}
