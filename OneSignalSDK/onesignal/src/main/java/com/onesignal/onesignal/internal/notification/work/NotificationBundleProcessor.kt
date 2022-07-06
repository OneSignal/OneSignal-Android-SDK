package com.onesignal.onesignal.internal.notification.work

import android.content.ContentValues
import android.content.Context
import android.os.Bundle
import androidx.annotation.WorkerThread
import com.onesignal.onesignal.OneSignal
import com.onesignal.onesignal.internal.application.IApplicationService
import com.onesignal.onesignal.internal.common.AndroidUtils
import com.onesignal.onesignal.internal.common.BundleCompat
import com.onesignal.onesignal.internal.common.time.ITime
import com.onesignal.onesignal.internal.database.IDatabase
import com.onesignal.onesignal.internal.database.OneSignalDbContract
import com.onesignal.onesignal.internal.notification.NotificationConstants
import com.onesignal.onesignal.internal.notification.NotificationFormatHelper
import com.onesignal.onesignal.internal.notification.data.NotificationDataController
import com.onesignal.onesignal.internal.notification.generation.IGenerateNotification
import com.onesignal.onesignal.internal.notification.receipt.IReceiveReceiptService
import com.onesignal.onesignal.internal.params.IParamsService
import com.onesignal.onesignal.logging.Logging
import com.onesignal.onesignal.notification.INotificationWillShowInForegroundHandler
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Processes the Bundle received from a push.
 * This class handles both processing bundles from a BroadcastReceiver or from a Service
 * - Entry points are processBundleFromReceiver or ProcessFromFCMIntentService respectively
 * NOTE: Could split up this class since it does a number of different things
 */
internal class NotificationBundleProcessor(
    private val _applicationService: IApplicationService,
    private val _notificationGeneration: IGenerateNotification,
    private val _workManager: NotificationWorkManager,
    private val _dataController: NotificationDataController,
    private val _receiptService: IReceiveReceiptService,
    private val _paramsService: IParamsService,
    private val _database: IDatabase,
    private val _time: ITime,
    private val _notificationWillShowInForegroundHandler: INotificationWillShowInForegroundHandler?
) {
    companion object {
        const val PUSH_ADDITIONAL_DATA_KEY = "a"
        const val PUSH_MINIFIED_BUTTONS_LIST = "o"
        const val PUSH_MINIFIED_BUTTON_ID = "i"
        const val PUSH_MINIFIED_BUTTON_TEXT = "n"
        const val PUSH_MINIFIED_BUTTON_ICON = "p"
        private const val ANDROID_NOTIFICATION_ID = "android_notif_id"
        const val IAM_PREVIEW_KEY = "os_in_app_message_preview_id"
        const val DEFAULT_ACTION = "__DEFAULT__"

        fun bundleAsJSONObject(bundle: Bundle): JSONObject {
            val json = JSONObject()
            val keys = bundle.keySet()
            for (key in keys) {
                try {
                    json.put(key, bundle[key])
                } catch (e: JSONException) {
                    Logging.error("bundleAsJSONObject error for key: $key", e)
                }
            }
            return json
        }
    }

    suspend fun processFromFCMIntentService(context: Context?, bundle: BundleCompat<*>) {
        OneSignal.initWithContext(context!!)

        try {
            val jsonStrPayload: String = bundle.getString("json_payload")
            if (jsonStrPayload == null) {
                Logging.error("json_payload key is nonexistent from mBundle passed to ProcessFromFCMIntentService: $bundle")
                return
            }
            val jsonPayload = JSONObject(jsonStrPayload)
            val isRestoring = bundle.getBoolean("is_restoring", false)
            val shownTimeStamp = bundle.getLong("timestamp")
            var androidNotificationId = 0
            if (bundle.containsKey(ANDROID_NOTIFICATION_ID)) androidNotificationId = bundle.getInt(
                ANDROID_NOTIFICATION_ID
            )
            val finalAndroidNotificationId = androidNotificationId

            val result = _dataController.notValidOrDuplicated(jsonPayload)
            if (!isRestoring && result)
                return

            val osNotificationId = NotificationFormatHelper.getOSNotificationIdFromJson(jsonPayload)
            _workManager.beginEnqueueingWork(
                context,
                osNotificationId!!,
                finalAndroidNotificationId,
                jsonStrPayload,
                shownTimeStamp,
                isRestoring,
                false
            )

            // Delay to prevent CPU spikes.
            // Normally more than one notification is restored at a time.
            if (isRestoring)
                AndroidUtils.sleep(100)
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    /**
     * Recommended method to process notification before displaying
     * Only use the [NotificationBundleProcessor.processJobForDisplay]
     * in the event where you want to mark a notification as opened or displayed different than the defaults
     */
    @WorkerThread
    fun processJobForDisplay(notificationJob: NotificationGenerationJob, fromBackgroundLogic: Boolean): Int {
        val notificationController = NotificationController(this, notificationJob, _paramsService, _time, notificationJob.isRestoring, true)
        return processJobForDisplay(notificationController, false, fromBackgroundLogic)
    }

    @WorkerThread
    fun processJobForDisplay(notificationController: NotificationController, fromBackgroundLogic: Boolean): Int {
        return processJobForDisplay(notificationController, false, fromBackgroundLogic)
    }

    @WorkerThread
    private fun processJobForDisplay(notificationController: NotificationController, opened: Boolean, fromBackgroundLogic: Boolean): Int {
        Logging.debug("Starting processJobForDisplay opened: $opened fromBackgroundLogic: $fromBackgroundLogic")

        val notificationJob = notificationController.notificationJob
        processCollapseKey(notificationJob)
        var androidNotificationId = notificationJob.androidIdWithoutCreate
        val doDisplay = shouldDisplayNotification(notificationJob)
        var notificationDisplayed = false
        if (doDisplay) {
            androidNotificationId = notificationJob.androidId

            if (fromBackgroundLogic && shouldFireForegroundHandlers(notificationJob)) {
                notificationController.isFromBackgroundLogic = false
                fireForegroundHandlers(notificationController)
                // Notification will be processed by foreground user complete or timer complete
                return androidNotificationId
            } else {
                // Notification might end not displaying because the channel for that notification has notification disable
                notificationDisplayed = _notificationGeneration.displayNotification(notificationJob)
            }
        }

        if (!notificationJob.isRestoring) {
            processNotification(notificationJob, opened, notificationDisplayed)

            // No need to keep notification duplicate check on memory, we have database check at this point
            // Without removing duplicate, summary restoration might not happen
            val osNotificationId = NotificationFormatHelper.getOSNotificationIdFromJson(notificationController.notificationJob.jsonPayload)

            _workManager.removeNotificationIdProcessed(osNotificationId!!)

            handleNotificationReceived(notificationJob)
        }
        return androidNotificationId
    }

    private fun shouldDisplayNotification(notificationJob: NotificationGenerationJob): Boolean {
        return notificationJob.hasExtender() || AndroidUtils.isStringNotEmpty(
            notificationJob.jsonPayload!!.optString("alert")
        )
    }

    fun handleNotificationReceived(notificationJob: NotificationGenerationJob) {

        // TODO: Implement
//            OneSignal.handleNotificationReceived(notificationJob)
    }

    /**
     * Save notification, updates Outcomes, and sends Received Receipt if they are enabled.
     */
    fun processNotification(
        notificationJob: NotificationGenerationJob,
        opened: Boolean,
        notificationDisplayed: Boolean
    ) {
        saveNotification(notificationJob, opened)
        if (!notificationDisplayed) {
            // Notification channel disable or not displayed
            // save notification as dismissed to avoid user re-enabling channel and notification being displayed due to restore
            markNotificationAsDismissed(notificationJob)
            return
        }

        // Logic for when the notification is displayed
        val notificationId = notificationJob.apiNotificationId
        _receiptService.notificationReceived(notificationId)

        // TODO: Implement
//        OneSignal.getSessionManager().onNotificationReceived(notificationId)
    }

    // Saving the notification provides the following:
    //   * Prevent duplicates
    //   * Build summary notifications
    //   * Collapse key / id support - Used to lookup the android notification id later
    //   * Redisplay notifications after reboot, upgrade of app, or cold boot after a force kill.
    //   * Future - Public API to get a list of notifications
    private fun saveNotification(notificationJob: NotificationGenerationJob, opened: Boolean) {
        Logging.debug("Saving Notification job: $notificationJob")

        val context = notificationJob.context
        val jsonPayload = notificationJob.jsonPayload!!
        try {
            val customJSON = getCustomJSONObject(jsonPayload)

            // Count any notifications with duplicated android notification ids as dismissed.
            // -1 is used to note never displayed
            if (notificationJob.isNotificationToDisplay) {
                val whereStr = OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + notificationJob.androidIdWithoutCreate
                val values = ContentValues()
                values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)

                _database.update(
                    OneSignalDbContract.NotificationTable.TABLE_NAME,
                    values,
                    whereStr,
                    null
                )

                // TODO: Implement
//                BadgeCountUpdater.update(dbHelper, context)
            }

            // Save just received notification to DB
            val values = ContentValues()
            values.put(
                OneSignalDbContract.NotificationTable.COLUMN_NAME_NOTIFICATION_ID,
                customJSON.optString("i")
            )
            if (jsonPayload.has("grp")) values.put(
                OneSignalDbContract.NotificationTable.COLUMN_NAME_GROUP_ID,
                jsonPayload.optString("grp")
            )
            if (jsonPayload.has("collapse_key") && "do_not_collapse" != jsonPayload.optString("collapse_key")) values.put(
                OneSignalDbContract.NotificationTable.COLUMN_NAME_COLLAPSE_ID,
                jsonPayload.optString("collapse_key")
            )
            values.put(
                OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED,
                if (opened) 1 else 0
            )
            if (!opened) values.put(
                OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID,
                notificationJob.androidIdWithoutCreate
            )
            if (notificationJob.title != null) values.put(
                OneSignalDbContract.NotificationTable.COLUMN_NAME_TITLE,
                notificationJob.title.toString()
            )
            if (notificationJob.body != null) values.put(
                OneSignalDbContract.NotificationTable.COLUMN_NAME_MESSAGE,
                notificationJob.body.toString()
            )

            // Set expire_time
            val sentTime = jsonPayload.optLong(
                NotificationConstants.GOOGLE_SENT_TIME_KEY,
                _time.currentTimeMillis
            ) / 1000L
            val ttl = jsonPayload.optInt(
                NotificationConstants.GOOGLE_TTL_KEY,
                NotificationConstants.DEFAULT_TTL_IF_NOT_IN_PAYLOAD
            )
            val expireTime = sentTime + ttl
            values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_EXPIRE_TIME, expireTime)
            values.put(
                OneSignalDbContract.NotificationTable.COLUMN_NAME_FULL_DATA,
                jsonPayload.toString()
            )

            _database.insertOrThrow(OneSignalDbContract.NotificationTable.TABLE_NAME, null, values)
            Logging.debug("Notification saved values: $values")

            if (!opened) {
                // TODO: Implement
                //BadgeCountUpdater.update(dbHelper, context)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun markNotificationAsDismissed(notifiJob: NotificationGenerationJob) {
        if (notifiJob.androidIdWithoutCreate == -1)
            return

        Logging.debug("Marking restored or disabled notifications as dismissed: $notifiJob")

        val whereStr =
            OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID + " = " + notifiJob.androidIdWithoutCreate

        val values = ContentValues()
        values.put(OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED, 1)
        _database.update(OneSignalDbContract.NotificationTable.TABLE_NAME, values, whereStr, null)

        // TODO: Implement
        //BadgeCountUpdater.update(dbHelper, notifiJob.context)
    }

    // Format our short keys into more readable ones.
    private fun maximizeButtonsFromBundle(fcmBundle: Bundle) {
        if (!fcmBundle.containsKey("o")) return
        try {
            val customJSON = JSONObject(fcmBundle.getString("custom"))
            val additionalDataJSON: JSONObject
            additionalDataJSON =
                if (customJSON.has(PUSH_ADDITIONAL_DATA_KEY)) customJSON.getJSONObject(
                    PUSH_ADDITIONAL_DATA_KEY
                ) else JSONObject()
            val buttons = JSONArray(fcmBundle.getString(PUSH_MINIFIED_BUTTONS_LIST))
            fcmBundle.remove(PUSH_MINIFIED_BUTTONS_LIST)
            for (i in 0 until buttons.length()) {
                val button = buttons.getJSONObject(i)
                val buttonText = button.getString(PUSH_MINIFIED_BUTTON_TEXT)
                button.remove(PUSH_MINIFIED_BUTTON_TEXT)
                var buttonId: String?
                if (button.has(PUSH_MINIFIED_BUTTON_ID)) {
                    buttonId = button.getString(PUSH_MINIFIED_BUTTON_ID)
                    button.remove(PUSH_MINIFIED_BUTTON_ID)
                } else buttonId = buttonText
                button.put("id", buttonId)
                button.put("text", buttonText)
                if (button.has(PUSH_MINIFIED_BUTTON_ICON)) {
                    button.put("icon", button.getString(PUSH_MINIFIED_BUTTON_ICON))
                    button.remove(PUSH_MINIFIED_BUTTON_ICON)
                }
            }
            additionalDataJSON.put("actionButtons", buttons)
            additionalDataJSON.put(NotificationConstants.GENERATE_NOTIFICATION_BUNDLE_KEY_ACTION_ID, DEFAULT_ACTION)
            if (!customJSON.has(PUSH_ADDITIONAL_DATA_KEY)) customJSON.put(
                PUSH_ADDITIONAL_DATA_KEY,
                additionalDataJSON
            )
            fcmBundle.putString("custom", customJSON.toString())
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun processCollapseKey(notificationJob: NotificationGenerationJob) {
        if (notificationJob.isRestoring) return
        if (notificationJob.jsonPayload?.has("collapse_key") == true || "do_not_collapse" == notificationJob.jsonPayload?.optString(
                "collapse_key"
            )
        ) return
        val collapse_id = notificationJob.jsonPayload!!.optString("collapse_key")

        val cursor = _database.query(
            OneSignalDbContract.NotificationTable.TABLE_NAME,
            arrayOf(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID),  // retColumn
            OneSignalDbContract.NotificationTable.COLUMN_NAME_COLLAPSE_ID + " = ? AND " +
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_DISMISSED + " = 0 AND " +
                    OneSignalDbContract.NotificationTable.COLUMN_NAME_OPENED + " = 0 ",
            arrayOf(collapse_id),
            null,
            null,
            null
        )
        if (cursor.moveToFirst()) {
            val androidNotificationId =
                cursor.getInt(cursor.getColumnIndex(OneSignalDbContract.NotificationTable.COLUMN_NAME_ANDROID_NOTIFICATION_ID))
            notificationJob.setAndroidIdWithoutOverriding(androidNotificationId)
        }
        cursor.close()
    }

    /**
     * Process bundle passed from FCM / HMS / ADM broadcast receiver
     */
    suspend fun processBundleFromReceiver(
        context: Context,
        bundle: Bundle,
        bundleReceiverCallback: ProcessBundleReceiverCallback
    ) {
        val bundleResult = ProcessedBundleResult()

        // Not a OneSignal GCM message
        if (!NotificationFormatHelper.isOneSignalBundle(bundle)) {
            bundleReceiverCallback.onBundleProcessed(bundleResult)
            return
        }
        bundleResult.setOneSignalPayload(true)
        maximizeButtonsFromBundle(bundle)

        // TODO: Implement
//        if (OSInAppMessagePreviewHandler.inAppMessagePreviewHandled(context, bundle)) {
//            // Return early, we don't want the extender service or etc. to fire for IAM previews
//            bundleResult.setInAppPreviewShown(true)
//            bundleReceiverCallback.onBundleProcessed(bundleResult)
//            return
//        }

        val processingCallback: NotificationProcessingCallback =
            object : NotificationProcessingCallback {
                override fun onResult(notificationProcessed: Boolean) {
                    // Bundle already non null, checked under isOneSignalBundle
                    if (!notificationProcessed) {
                        // We already check for bundle == null under isOneSignalBundle
                        // At this point we know notification is duplicate
                        bundleResult.isDup = true
                    }
                    bundleReceiverCallback.onBundleProcessed(bundleResult)
                }
            }
        startNotificationProcessing(context, bundle, bundleResult, processingCallback)
    }

    private suspend fun startNotificationProcessing(
        context: Context,
        bundle: Bundle,
        bundleResult: ProcessedBundleResult,
        notificationProcessingCallback: NotificationProcessingCallback
    ) {
        val jsonPayload = bundleAsJSONObject(bundle)
        val timestamp = _time.currentTimeMillis / 1000L
        val isRestoring = bundle.getBoolean("is_restoring", false)
        val isHighPriority = bundle.getString("pri", "0").toInt() > 9

        val result = _dataController.notValidOrDuplicated(jsonPayload)

        if (!isRestoring
            && result
        ) {
            Logging.debug("startNotificationProcessing returning, with context: $context and bundle: $bundle")
            notificationProcessingCallback.onResult(false)
            return
        }
        val osNotificationId = NotificationFormatHelper.getOSNotificationIdFromJson(jsonPayload)
        var androidNotificationId = 0
        if (bundle.containsKey(ANDROID_NOTIFICATION_ID)) androidNotificationId =
            bundle.getInt(
                ANDROID_NOTIFICATION_ID
            )
        _workManager.beginEnqueueingWork(
            context,
            osNotificationId!!,
            androidNotificationId,
            jsonPayload.toString(),
            timestamp,
            isRestoring,
            isHighPriority
        )
        bundleResult.isWorkManagerProcessing = true
        notificationProcessingCallback.onResult(true)
    }

    fun newJsonArray(jsonObject: JSONObject?): JSONArray {
        return JSONArray().put(jsonObject)
    }

    @Throws(JSONException::class)
    fun getCustomJSONObject(jsonObject: JSONObject): JSONObject {
        return JSONObject(jsonObject.optString("custom"))
    }

    fun hasRemoteResource(bundle: Bundle): Boolean {
        return (isBuildKeyRemote(bundle, "licon")
                || isBuildKeyRemote(bundle, "bicon")
                || bundle.getString("bg_img", null) != null)
    }

    private fun isBuildKeyRemote(bundle: Bundle, key: String): Boolean {
        val value = bundle.getString(key, "").trim { it <= ' ' }
        return value.startsWith("http://") || value.startsWith("https://")
    }

    fun fireForegroundHandlers(notificationController: NotificationController) {
        Logging.info("Fire notificationWillShowInForegroundHandler")

        val notificationReceivedEvent = notificationController.notificationReceivedEvent

        try {
            _notificationWillShowInForegroundHandler!!.notificationWillShowInForeground(notificationReceivedEvent)
        } catch (t: Throwable) {
            Logging.error("Exception thrown while notification was being processed for display by notificationWillShowInForegroundHandler, showing notification in foreground!")
            notificationReceivedEvent.complete(notificationReceivedEvent.notification)
            throw t
        }
    }

    fun shouldFireForegroundHandlers(notificationJob: NotificationGenerationJob): Boolean {
        if (!_applicationService.isInForeground) {
            Logging.info("App is in background, show notification")
            return false
        }
        if (_notificationWillShowInForegroundHandler == null) {
            Logging.info("No NotificationWillShowInForegroundHandler setup, show notification")
            return false
        }

        // Notification is restored. Don't fire for restored notifications.
        if (notificationJob.isRestoring) {
            Logging.info("Not firing notificationWillShowInForegroundHandler for restored notifications")
            return false
        }
        return true
    }

    internal class ProcessedBundleResult {
        private var isOneSignalPayload = false
        var isDup = false
        private var inAppPreviewShown = false
        var isWorkManagerProcessing = false
        fun processed(): Boolean {
            return !isOneSignalPayload || isDup || inAppPreviewShown || isWorkManagerProcessing
        }

        fun setOneSignalPayload(oneSignalPayload: Boolean) {
            isOneSignalPayload = oneSignalPayload
        }

        fun setInAppPreviewShown(inAppPreviewShown: Boolean) {
            this.inAppPreviewShown = inAppPreviewShown
        }
    }

    internal interface ProcessBundleReceiverCallback {
        /**
         * @param processedResult the processed bundle result
         */
        fun onBundleProcessed(processedResult: ProcessedBundleResult?)
    }

    internal interface NotificationProcessingCallback {
        /**
         * @param notificationProcessed is true if notification was processed, otherwise false
         */
        fun onResult(notificationProcessed: Boolean)
    }
}