package com.onesignal.onesignal.notification.internal.receivereceipt

import android.app.Activity
import android.content.Context
import androidx.work.*
import androidx.work.CoroutineWorker
import com.onesignal.onesignal.core.OneSignal
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.backend.api.ApiException
import com.onesignal.onesignal.core.internal.common.AndroidUtils
import com.onesignal.onesignal.core.internal.device.IDeviceService
import com.onesignal.onesignal.core.internal.models.ConfigModel
import com.onesignal.onesignal.core.internal.params.IParamsService
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.onesignal.core.internal.service.IStartableService
import com.onesignal.onesignal.core.user.IUserManager
import com.onesignal.onesignal.notification.internal.generation.NotificationGenerationJob
import com.onesignal.onesignal.notification.internal.lifecycle.INotificationLifecycleEventHandler
import com.onesignal.onesignal.notification.internal.lifecycle.INotificationLifecycleService
import org.json.JSONArray
import java.lang.NullPointerException
import java.util.concurrent.TimeUnit

internal class ReceiveReceiptService(
        private val _paramsService: IParamsService,
        private val _applicationService: IApplicationService,
        private val _lifecycleService: INotificationLifecycleService) :
    INotificationLifecycleEventHandler,
    IStartableService {

    private val minDelay = 0
    private val maxDelay = 25

    override suspend fun start() {
        _lifecycleService.subscribe(this)
    }

    override fun onOpened(activity: Activity, data: JSONArray, notificationId: String) {}

    override fun onReceived(notificationJob: NotificationGenerationJob) {
        val osNotificationId = notificationJob.apiNotificationId

        if (!_paramsService.receiveReceiptEnabled) {
            Logging.debug("sendReceiveReceipt disabled")
            return
        }
        val delay: Int = AndroidUtils.getRandomDelay(minDelay, maxDelay)
        val inputData = Data.Builder()
            .putString(OS_NOTIFICATION_ID, osNotificationId)
            .build()
        val constraints = buildConstraints()
        val workRequest = OneTimeWorkRequest.Builder(ReceiveReceiptWorker::class.java)
            .setConstraints(constraints)
            .setInitialDelay(delay.toLong(), TimeUnit.SECONDS)
            .setInputData(inputData)
            .build()
        Logging.debug("OSReceiveReceiptController enqueueing send receive receipt work with notificationId: $osNotificationId and delay: $delay seconds")
        WorkManager.getInstance(_applicationService.appContext!!)
            .enqueueUniqueWork(
                osNotificationId + "_receive_receipt",
                ExistingWorkPolicy.KEEP,
                workRequest
            )
    }

    private fun buildConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }

    class ReceiveReceiptWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
        private var _deviceService: IDeviceService = OneSignal.getService()
        private var _userManager: IUserManager = OneSignal.getService()
        private var _config: ConfigModel = OneSignal.getService<ConfigModelStore>().get()

        override suspend fun doWork(): Result {
            val inputData = inputData
            val notificationId = inputData.getString(OS_NOTIFICATION_ID)
            sendReceiveReceipt(notificationId!!)
            return Result.success()
        }

        private suspend fun sendReceiveReceipt(notificationId: String) {
            val appId: String = _config!!.appId ?: ""
            val playerId: String = _userManager!!.subscriptions.push?.id.toString()

            var deviceType: Int? = null

            try {
                deviceType = _deviceService!!.deviceType
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }

            val finalDeviceType = deviceType
            Logging.debug("ReceiveReceiptWorker: Device Type is: $finalDeviceType")

            try {
                // TODO: Implement
                //_apiService!!.updateNotificationAsReceived(appId, notificationId, playerId, deviceType)
                Logging.debug("Receive receipt sent for notificationID: $notificationId")
            }
            catch (ae: ApiException) {
                Logging.error("Receive receipt failed with statusCode: ${ae.statusCode} response: $ae.response")
            }
        }
    }

    companion object {
        private const val OS_NOTIFICATION_ID = "os_notification_id"
    }
}