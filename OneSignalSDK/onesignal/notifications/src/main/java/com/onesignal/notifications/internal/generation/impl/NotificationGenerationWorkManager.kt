package com.onesignal.notifications.internal.generation.impl

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import com.onesignal.OneSignal
import com.onesignal.OneSignal.ensureOneSignalInitialized
import com.onesignal.common.AndroidUtils
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.common.NotificationFormatHelper
import com.onesignal.notifications.internal.common.OSWorkManagerHelper
import com.onesignal.notifications.internal.generation.INotificationGenerationProcessor
import com.onesignal.notifications.internal.generation.INotificationGenerationWorkManager
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

internal class NotificationGenerationWorkManager : INotificationGenerationWorkManager {
    override fun beginEnqueueingWork(
        context: Context,
        osNotificationId: String,
        androidNotificationId: Int,
        jsonPayload: JSONObject?,
        timestamp: Long,
        isRestoring: Boolean,
        isHighPriority: Boolean,
    ): Boolean {
        val id: String? = NotificationFormatHelper.getOSNotificationIdFromJson(jsonPayload)

        if (id == null) {
            Logging.debug("Notification beginEnqueueingWork with id null")
            return false
        }

        if (!addNotificationIdProcessed(id)) {
            Logging.debug("Notification beginEnqueueingWork with id duplicated")
            return true
        }

        // TODO: Need to figure out how to implement the isHighPriority param
        val inputData =
            Data.Builder()
                .putString(OS_ID_DATA_PARAM, id)
                .putInt(ANDROID_NOTIF_ID_WORKER_DATA_PARAM, androidNotificationId)
                .putString(JSON_PAYLOAD_WORKER_DATA_PARAM, jsonPayload.toString())
                .putLong(TIMESTAMP_WORKER_DATA_PARAM, timestamp)
                .putBoolean(IS_RESTORING_WORKER_DATA_PARAM, isRestoring)
                .build()
        val workRequest =
            OneTimeWorkRequest.Builder(NotificationGenerationWorker::class.java)
                .setInputData(inputData)
                .build()
        Logging.debug(
            "NotificationWorkManager enqueueing notification work with notificationId: $osNotificationId and jsonPayload: $jsonPayload",
        )
        OSWorkManagerHelper.getInstance(context)
            .enqueueUniqueWork(osNotificationId, ExistingWorkPolicy.KEEP, workRequest)

        return true
    }

    class NotificationGenerationWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            if (!ensureOneSignalInitialized(applicationContext)) {
                Logging.warn("NotificationWorker skipped due to failed OneSignal initialization")
                return Result.success()
            }

            val notificationProcessor: INotificationGenerationProcessor = OneSignal.getService()
            val inputData = inputData
            val id = inputData.getString(OS_ID_DATA_PARAM) ?: return Result.failure()

            return try {
                Logging.debug("NotificationWorker running doWork with data: $inputData")

                val androidNotificationId = inputData.getInt(ANDROID_NOTIF_ID_WORKER_DATA_PARAM, 0)
                val jsonPayload = JSONObject(inputData.getString(JSON_PAYLOAD_WORKER_DATA_PARAM))
                val timestamp =
                    inputData.getLong(
                        TIMESTAMP_WORKER_DATA_PARAM,
                        System.currentTimeMillis() / 1000L,
                    )
                val isRestoring = inputData.getBoolean(IS_RESTORING_WORKER_DATA_PARAM, false)

                notificationProcessor.processNotificationData(
                    applicationContext,
                    androidNotificationId,
                    jsonPayload,
                    isRestoring,
                    timestamp,
                )
                Result.success()
            } catch (e: JSONException) {
                Logging.error("Error occurred doing work for job with id: $id", e)
                Result.failure()
            } finally {
                removeNotificationIdProcessed(id!!)
            }
        }
    }

    companion object {
        private const val OS_ID_DATA_PARAM = "os_notif_id"
        private const val ANDROID_NOTIF_ID_WORKER_DATA_PARAM = "android_notif_id"
        private const val JSON_PAYLOAD_WORKER_DATA_PARAM = "json_payload"
        private const val TIMESTAMP_WORKER_DATA_PARAM = "timestamp"
        private const val IS_RESTORING_WORKER_DATA_PARAM = "is_restoring"

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
                    notificationIds[osNotificationId] = true
                }
            }
            return true
        }

        fun removeNotificationIdProcessed(osNotificationId: String) {
            if (AndroidUtils.isStringNotEmpty(osNotificationId)) {
                notificationIds.remove(osNotificationId)
            }
        }
    }
}
