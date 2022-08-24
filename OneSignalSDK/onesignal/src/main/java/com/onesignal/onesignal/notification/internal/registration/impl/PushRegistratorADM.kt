package com.onesignal.onesignal.notification.internal.registration.impl

import com.amazon.device.messaging.ADM
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.notification.internal.registration.IPushRegistrator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

class PushRegistratorADM(
    private val _applicationService: IApplicationService
) : IPushRegistrator, IPushRegistratorCallback {

    private var _channel: Channel<String?>? = null

    override suspend fun registerForPush() : IPushRegistrator.RegisterResult = coroutineScope {
        var result: IPushRegistrator.RegisterResult? = null

        _channel = Channel()
        launch(Dispatchers.Default) {
            val adm = ADM(_applicationService.appContext)
            var registrationId = adm.registrationId
            if (registrationId != null) {
                Logging.debug("ADM Already registered with ID:$registrationId")
                result = IPushRegistrator.RegisterResult(
                    registrationId,
                    IPushRegistrator.RegisterStatus.PUSH_STATUS_SUBSCRIBED
                )
            }
            else {
                adm.startRegister()

                // wait up to 30 seconds for someone to call `fireCallback` with the registration id.
                // if it comes before we will continue immediately.
                withTimeout(30000) {
                    registrationId = _channel?.receive()
                }

                result = if(registrationId != null) {
                    Logging.error("ADM registered with ID:$registrationId")
                    IPushRegistrator.RegisterResult(
                        registrationId,
                        IPushRegistrator.RegisterStatus.PUSH_STATUS_SUBSCRIBED
                    )
                } else {
                    Logging.error("com.onesignal.ADMMessageHandler timed out, please check that your have the receiver, service, and your package name matches(NOTE: Case Sensitive) per the OneSignal instructions.")
                    IPushRegistrator.RegisterResult(
                        null,
                        IPushRegistrator.RegisterStatus.PUSH_STATUS_ERROR
                    )
                }
            }
        }

        return@coroutineScope result!!
    }

    override suspend fun fireCallback(id: String?) {
        _channel?.send(id)
    }
}