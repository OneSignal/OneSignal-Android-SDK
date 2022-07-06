package com.onesignal.onesignal.internal.notification.work

import android.content.Context
import androidx.work.*
import com.onesignal.onesignal.OneSignal
import com.onesignal.onesignal.internal.common.AndroidUtils
import com.onesignal.onesignal.internal.common.time.ITime
import com.onesignal.onesignal.internal.notification.Notification
import com.onesignal.onesignal.internal.notification.NotificationReceivedEvent
import com.onesignal.onesignal.internal.params.IParamsService
import com.onesignal.onesignal.logging.Logging
import com.onesignal.onesignal.notification.IRemoteNotificationReceivedHandler
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class NotificationWorkManager {
    private val notificationIds = ConcurrentHashMap<String, Boolean>()

    fun addNotificationIdProcessed(osNotificationId: String): Boolean {
        // Duplicate control
        // Keep in memory on going processed notifications, to avoid fast duplicates that already finished work process but are not completed yet
        // enqueueUniqueWork might not be enough, if the work already finished then the duplicate notification work might be queued again
        if (AndroidUtils.isStringNotEmpty(osNotificationId)) {
            if (notificationIds.contains(osNotificationId)) {
                Logging.debug("OSNotificationWorkManager notification with notificationId: $osNotificationId already queued")
                return false
            } else {
                notificationIds.put(osNotificationId, true)
            }
        }
        return true
    }

    fun removeNotificationIdProcessed(osNotificationId: String) {
        if (AndroidUtils.isStringNotEmpty(osNotificationId)) {
            notificationIds.remove(osNotificationId)
        }
    }

    fun beginEnqueueingWork(
        context: Context?,
        osNotificationId: String,
        androidNotificationId: Int,
        jsonPayload: String,
        timestamp: Long,
        isRestoring: Boolean,
        isHighPriority: Boolean
    ) {
        // TODO: Need to figure out how to implement the isHighPriority param
        val inputData = Data.Builder()
            .putInt(ANDROID_NOTIF_ID_WORKER_DATA_PARAM, androidNotificationId)
            .putString(JSON_PAYLOAD_WORKER_DATA_PARAM, jsonPayload)
            .putLong(TIMESTAMP_WORKER_DATA_PARAM, timestamp)
            .putBoolean(IS_RESTORING_WORKER_DATA_PARAM, isRestoring)
            .build()
        val workRequest = OneTimeWorkRequest.Builder(NotificationWorker::class.java)
            .setInputData(inputData)
            .build()
        Logging.debug("OSNotificationWorkManager enqueueing notification work with notificationId: $osNotificationId and jsonPayload: $jsonPayload")
        WorkManager.getInstance(context!!)
            .enqueueUniqueWork(osNotificationId, ExistingWorkPolicy.KEEP, workRequest)
    }

    class NotificationWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

        private val _notificationProcessor: NotificationBundleProcessor = OneSignal.getService()
        private val _remoteNotificationReceivedHandler: IRemoteNotificationReceivedHandler? = OneSignal.getServiceOrNull()
        private val _time: ITime = OneSignal.getService()
        private val _paramsService: IParamsService = OneSignal.getService()

        override suspend fun doWork(): Result {
            val inputData = inputData
            try {
                Logging.debug("NotificationWorker running doWork with data: $inputData")
                val androidNotificationId = inputData.getInt(ANDROID_NOTIF_ID_WORKER_DATA_PARAM, 0)
                val jsonPayload = JSONObject(inputData.getString(JSON_PAYLOAD_WORKER_DATA_PARAM))
                val timestamp = inputData.getLong(
                    TIMESTAMP_WORKER_DATA_PARAM,
                    System.currentTimeMillis() / 1000L
                )
                val isRestoring = inputData.getBoolean(IS_RESTORING_WORKER_DATA_PARAM, false)
                processNotificationData(
                    applicationContext,
                    androidNotificationId,
                    jsonPayload,
                    isRestoring,
                    timestamp
                )
            } catch (e: JSONException) {
                Logging.error("Error occurred doing work for job with id: $id")
                e.printStackTrace()
                return Result.failure()
            }
            return Result.success()
        }

        private fun processNotificationData(
            context: Context, androidNotificationId: Int, jsonPayload: JSONObject,
            isRestoring: Boolean, timestamp: Long
        ) {
            val notification = Notification(null, jsonPayload, androidNotificationId, _time)
            val controller = NotificationController(
                _notificationProcessor,
                context,
                notification,
                jsonPayload,
                isRestoring,
                true,
                timestamp,
                _paramsService,
                _time,
            )
            val notificationReceived = NotificationReceivedEvent(controller, notification)
            if (_remoteNotificationReceivedHandler != null) try {
                _remoteNotificationReceivedHandler.remoteNotificationReceived(context, notificationReceived
                )
            } catch (t: Throwable) {
                Logging.error("remoteNotificationReceived throw an exception. Displaying normal OneSignal notification.", t)
                notificationReceived.complete(notification)
                throw t
            } else {
                Logging.warn("remoteNotificationReceivedHandler not setup, displaying normal OneSignal notification")
                notificationReceived.complete(notification)
            }
        }
    }


    companion object {
        private const val ANDROID_NOTIF_ID_WORKER_DATA_PARAM = "android_notif_id"
        private const val JSON_PAYLOAD_WORKER_DATA_PARAM = "json_payload"
        private const val TIMESTAMP_WORKER_DATA_PARAM = "timestamp"
        private const val IS_RESTORING_WORKER_DATA_PARAM = "is_restoring"
    }

}