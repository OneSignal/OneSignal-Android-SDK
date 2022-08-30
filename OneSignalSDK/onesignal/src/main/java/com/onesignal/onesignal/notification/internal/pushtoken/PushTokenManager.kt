package com.onesignal.onesignal.notification.internal.pushtoken

import com.onesignal.onesignal.core.internal.common.events.EventProducer
import com.onesignal.onesignal.notification.internal.registration.IPushRegistrator

/**
 * The notification manager is responsible for the management of notifications
 * on the current device (not the user).
 */
internal class PushTokenManager(
    private val _pushRegistrator: IPushRegistrator,
) : IPushTokenManager {

    private val _pushTokenChangedNotifier = EventProducer<IPushTokenChangedHandler>()
    override var pushToken: String? = null

    override suspend fun retrievePushToken() {
        // if there's already a push token, nothing to do.
        if (pushToken != null)
            return

        val registerResult = _pushRegistrator.registerForPush()

        if (registerResult.status < IPushRegistrator.RegisterStatus.PUSH_STATUS_SUBSCRIBED) {
            // Only allow errored subscribableStatuses if we have never gotten a token.
            //   This ensures the device will not later be marked unsubscribed due to a
            //   any inconsistencies returned by Google Play services.
            // Also do not override a config error status if we got a runtime error
//  TODO          if (OneSignalStateSynchronizer.getRegistrationId() == null &&
//                (OneSignal.subscribableStatus == UserState.PUSH_STATUS_SUBSCRIBED ||
//                        OneSignal.pushStatusRuntimeError(OneSignal.subscribableStatus))
//            ) OneSignal.subscribableStatus = status
        }
//  TODO    else if (OneSignal.pushStatusRuntimeError(OneSignal.subscribableStatus))
//            OneSignal.subscribableStatus = status

        // TODO: What if no result or the push registration fails?
        if (registerResult.status == IPushRegistrator.RegisterStatus.PUSH_STATUS_SUBSCRIBED) {
            pushToken = registerResult.id!!
            _pushTokenChangedNotifier.fire { it.onPushTokenChanged(pushToken) }
        }
    }

    override fun subscribe(handler: IPushTokenChangedHandler) = _pushTokenChangedNotifier.subscribe(handler)
    override fun unsubscribe(handler: IPushTokenChangedHandler) = _pushTokenChangedNotifier.unsubscribe(handler)
}
