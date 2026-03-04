package com.onesignal.notifications.internal.registration.impl

import android.content.Context
import android.text.TextUtils
import com.huawei.agconnect.config.AGConnectServicesConfig
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.common.ApiException
import com.huawei.hms.push.HmsMessaging
import com.huawei.hms.support.api.entity.core.CommonCode
import com.onesignal.common.threading.WaiterWithValue
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.registration.IPushRegistrator
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import kotlinx.coroutines.withTimeout

internal class PushRegistratorHMS(
    private val _deviceService: IDeviceService,
    private val _applicationService: IApplicationService,
) : IPushRegistrator,
    IPushRegistratorCallback {
    companion object {
        private const val HMS_CLIENT_APP_ID = "client/app_id"
    }

    private var waiter: WaiterWithValue<String?>? = null

    override suspend fun registerForPush(): IPushRegistrator.RegisterResult {
        var result: IPushRegistrator.RegisterResult? = null

        result =
            try {
                getHMSTokenTask(_applicationService.appContext)
            } catch (e: ApiException) {
                Logging.error("HMS ApiException getting Huawei push token!", e)
                val pushStatus: SubscriptionStatus =
                    if (e.statusCode == CommonCode.ErrorCode.ARGUMENTS_INVALID) {
                        SubscriptionStatus.HMS_ARGUMENTS_INVALID
                    } else {
                        SubscriptionStatus.HMS_API_EXCEPTION_OTHER
                    }

                IPushRegistrator.RegisterResult(null, pushStatus)
            }

        return result!!
    }

    @Throws(ApiException::class)
    private suspend fun getHMSTokenTask(context: Context): IPushRegistrator.RegisterResult {
        // Check required to prevent AGConnectServicesConfig or HmsInstanceId used below
        //   from throwing a ClassNotFoundException
        if (!_deviceService.hasAllHMSLibrariesForPushKit) {
            return IPushRegistrator.RegisterResult(
                null,
                SubscriptionStatus.MISSING_HMS_PUSHKIT_LIBRARY,
            )
        }

        waiter = WaiterWithValue()
        val appId = AGConnectServicesConfig.fromContext(context).getString(HMS_CLIENT_APP_ID)
        val hmsInstanceId = HmsInstanceId.getInstance(context)
        var pushToken = hmsInstanceId.getToken(appId, HmsMessaging.DEFAULT_TOKEN_SCOPE)
        if (!TextUtils.isEmpty(pushToken)) {
            Logging.info("Device registered for HMS, push token = $pushToken")
            return IPushRegistrator.RegisterResult(
                pushToken,
                SubscriptionStatus.SUBSCRIBED,
            )
        } else {
            // If EMUI 9.x or older getToken will always return null.
            // We must wait for HmsMessageService.onNewToken to fire instead.
            // wait up to 30 seconds for someone to call `fireCallback` with the registration id.
            // if it comes before we will continue immediately.
            withTimeout(30000) {
                pushToken = waiter?.waitForWake()
            }

            return if (pushToken != null) {
                Logging.error("HMS registered with ID:$pushToken")
                IPushRegistrator.RegisterResult(
                    pushToken,
                    SubscriptionStatus.SUBSCRIBED,
                )
            } else {
                Logging.error("HmsMessageServiceOneSignal.onNewToken timed out.")
                IPushRegistrator.RegisterResult(
                    null,
                    SubscriptionStatus.HMS_TOKEN_TIMEOUT,
                )
            }
        }
    }

    override suspend fun fireCallback(id: String?) {
        waiter?.wake(id)
    }
}
