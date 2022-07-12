package com.onesignal.onesignal.notification.internal.receipt

import android.content.Context
import androidx.work.*
import androidx.work.CoroutineWorker
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.backend.api.ApiException
import com.onesignal.onesignal.core.internal.backend.api.IApiService
import com.onesignal.onesignal.core.internal.common.AndroidUtils
import com.onesignal.onesignal.core.internal.device.IDeviceService
import com.onesignal.onesignal.core.internal.models.ConfigModel
import com.onesignal.onesignal.core.internal.params.IParamsService
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.user.IUserManager
import java.lang.NullPointerException
import java.util.concurrent.TimeUnit

internal class ReceiveReceiptService(
        private val _paramsService: IParamsService,
        private val _applicationService: IApplicationService) :
    IReceiveReceiptService {
    private val minDelay = 0
    private val maxDelay = 25

    override fun notificationReceived(osNotificationId: String) {
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

        // TODO: Need a way to get these services
        private var _deviceService: IDeviceService? = null
        private var _apiService: IApiService? = null
        private var _userManager: IUserManager? = null
        private var _config: ConfigModel? = null

        override suspend fun doWork(): Result {
            val inputData = inputData
            val notificationId = inputData.getString(OS_NOTIFICATION_ID)
            sendReceiveReceipt(notificationId!!)
            return Result.success()
        }

        private suspend fun sendReceiveReceipt(notificationId: String) {
            val appId: String = _config!!.appId ?: ""
            val playerId: String = _userManager!!.subscriptions.thisDevice?.id.toString()

            var deviceType: Int? = null

            try {
                deviceType = _deviceService!!.getDeviceType()
            } catch (e: NullPointerException) {
                e.printStackTrace()
            }

            val finalDeviceType = deviceType
            Logging.debug("ReceiveReceiptWorker: Device Type is: $finalDeviceType")

            try {
                _apiService!!.updateNotificationAsReceived(appId, notificationId, playerId, deviceType)
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