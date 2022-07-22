package com.onesignal.onesignal.notification.internal.registration

import android.content.Context
import android.text.TextUtils
import com.huawei.agconnect.config.AGConnectServicesConfig
import com.huawei.hms.aaid.HmsInstanceId
import com.huawei.hms.common.ApiException
import com.huawei.hms.push.HmsMessaging
import com.huawei.hms.support.api.entity.core.CommonCode
import com.onesignal.onesignal.core.internal.device.IDeviceService
import com.onesignal.onesignal.core.internal.logging.Logging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal class PushRegistratorHMS(private val _deviceService: IDeviceService) : IPushRegistrator {

    companion object {
        private const val HMS_CLIENT_APP_ID = "client/app_id"
    }

    private var _channel: Channel<String?>? = null

    override suspend fun registerForPush(context: Context) : IPushRegistrator.RegisterResult = coroutineScope {
        var result: IPushRegistrator.RegisterResult? = null

        launch(Dispatchers.Default) {
            result = try {
                getHMSTokenTask(context)
            } catch (e: ApiException) {
                Logging.error("HMS ApiException getting Huawei push token!", e)
                val pushStatus: IPushRegistrator.RegisterStatus =
                    if (e.statusCode == CommonCode.ErrorCode.ARGUMENTS_INVALID)
                        IPushRegistrator.RegisterStatus.PUSH_STATUS_HMS_ARGUMENTS_INVALID
                    else
                        IPushRegistrator.RegisterStatus.PUSH_STATUS_HMS_API_EXCEPTION_OTHER

                IPushRegistrator.RegisterResult(null, pushStatus)
            }
        }

        return@coroutineScope result!!
    }

    @Synchronized
    @Throws(ApiException::class)
    private suspend fun getHMSTokenTask(context: Context) : IPushRegistrator.RegisterResult {
        // Check required to prevent AGConnectServicesConfig or HmsInstanceId used below
        //   from throwing a ClassNotFoundException
        if (!_deviceService.hasAllHMSLibrariesForPushKit()) {
            return IPushRegistrator.RegisterResult(
                null,
                IPushRegistrator.RegisterStatus.PUSH_STATUS_MISSING_HMS_PUSHKIT_LIBRARY
            )
        }

        _channel = Channel()
        val appId = AGConnectServicesConfig.fromContext(context).getString(HMS_CLIENT_APP_ID)
        val hmsInstanceId = HmsInstanceId.getInstance(context)
        var pushToken = hmsInstanceId.getToken(appId, HmsMessaging.DEFAULT_TOKEN_SCOPE)
        if (!TextUtils.isEmpty(pushToken)) {
            Logging.info("Device registered for HMS, push token = $pushToken")
            return IPushRegistrator.RegisterResult(
                pushToken,
                IPushRegistrator.RegisterStatus.PUSH_STATUS_SUBSCRIBED
            )
        } else {
            // If EMUI 9.x or older getToken will always return null.
            // We must wait for HmsMessageService.onNewToken to fire instead.
            // wait up to 30 seconds for someone to call `fireCallback` with the registration id.
            // if it comes before we will continue immediately.
            withTimeout(30000) {
                pushToken = _channel?.receive()
            }

            return if(pushToken != null) {
                Logging.error("ADM registered with ID:$pushToken")
                IPushRegistrator.RegisterResult(
                    pushToken,
                    IPushRegistrator.RegisterStatus.PUSH_STATUS_SUBSCRIBED
                )
            } else {
                Logging.error("HmsMessageServiceOneSignal.onNewToken timed out.")
                IPushRegistrator.RegisterResult(
                    null,
                    IPushRegistrator.RegisterStatus.PUSH_STATUS_HMS_TOKEN_TIMEOUT
                )
            }
        }
    }

    suspend fun fireCallback(id: String?) {
        _channel?.send(id)
    }
}