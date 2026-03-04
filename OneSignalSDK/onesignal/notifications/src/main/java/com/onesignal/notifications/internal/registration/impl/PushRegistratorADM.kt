package com.onesignal.notifications.internal.registration.impl

import com.amazon.device.messaging.ADM
import com.onesignal.common.threading.WaiterWithValue
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.registration.IPushRegistrator
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import kotlinx.coroutines.withTimeout

internal class PushRegistratorADM(
    private val _applicationService: IApplicationService,
) : IPushRegistrator, IPushRegistratorCallback {
    private var waiter: WaiterWithValue<String?>? = null

    override suspend fun registerForPush(): IPushRegistrator.RegisterResult {
        var result: IPushRegistrator.RegisterResult? = null

        waiter = WaiterWithValue()

        val adm = ADM(_applicationService.appContext)
        var registrationId = adm.registrationId
        if (registrationId != null) {
            Logging.debug("ADM Already registered with ID:$registrationId")
            result =
                IPushRegistrator.RegisterResult(
                    registrationId,
                    SubscriptionStatus.SUBSCRIBED,
                )
        } else {
            adm.startRegister()

            // wait up to 30 seconds for someone to call `fireCallback` with the registration id.
            // if it comes before we will continue immediately.
            withTimeout(30000) {
                registrationId = waiter?.waitForWake()
            }

            result =
                if (registrationId != null) {
                    Logging.debug("ADM registered with ID:$registrationId")
                    IPushRegistrator.RegisterResult(
                        registrationId,
                        SubscriptionStatus.SUBSCRIBED,
                    )
                } else {
                    Logging.info("com.onesignal.ADMMessageHandler timed out, please check that your have the receiver, service, and your package name matches(NOTE: Case Sensitive) per the OneSignal instructions.")
                    IPushRegistrator.RegisterResult(
                        null,
                        SubscriptionStatus.ERROR,
                    )
                }
        }

        return result!!
    }

    override suspend fun fireCallback(id: String?) {
        waiter?.wake(id)
    }
}
