package com.onesignal.notification.internal.pushtoken

import com.onesignal.core.internal.common.events.EventProducer
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.logging.Logging
import com.onesignal.notification.internal.registration.IPushRegistrator

/**
 * The notification manager is responsible for the management of notifications
 * on the current device (not the user).
 */
internal class PushTokenManager(
    private val _pushRegistrator: IPushRegistrator,
    private val _deviceService: IDeviceService
) : IPushTokenManager {

    private val _pushTokenChangedNotifier = EventProducer<IPushTokenChangedHandler>()

    override var pushTokenStatus: IPushRegistrator.RegisterStatus = IPushRegistrator.RegisterStatus.PUSH_STATUS_SUBSCRIBED
    override var pushToken: String? = null

    override suspend fun retrievePushToken() {
        when (_deviceService.androidSupportLibraryStatus) {
            IDeviceService.AndroidSupportLibraryStatus.MISSING -> {
                Logging.fatal("Could not find the Android Support Library. Please make sure it has been correctly added to your project.")
                pushTokenStatus = IPushRegistrator.RegisterStatus.PUSH_STATUS_MISSING_ANDROID_SUPPORT_LIBRARY
            }
            IDeviceService.AndroidSupportLibraryStatus.OUTDATED -> {
                Logging.fatal("The included Android Support Library is to old or incomplete. Please update to the 26.0.0 revision or newer.")
                pushTokenStatus = IPushRegistrator.RegisterStatus.PUSH_STATUS_OUTDATED_ANDROID_SUPPORT_LIBRARY
            }
            else -> {
                val registerResult = _pushRegistrator.registerForPush()

                if (registerResult.status.value < IPushRegistrator.RegisterStatus.PUSH_STATUS_SUBSCRIBED.value) {
                    // Only allow errored statuses if we have never gotten a token. This ensures the
                    // device will not later be marked unsubscribed due to any inconsistencies returned
                    // by Google Play services. Also do not override a config error status if we got a
                    // runtime error
                    if (pushToken == null &&
                        (
                            pushTokenStatus == IPushRegistrator.RegisterStatus.PUSH_STATUS_SUBSCRIBED ||
                                pushStatusRuntimeError(pushTokenStatus)
                            )
                    ) {
                        pushTokenStatus = registerResult.status
                    }
                } else if (pushStatusRuntimeError(pushTokenStatus)) {
                    pushTokenStatus = registerResult.status
                }

                pushToken = registerResult.id
            }
        }

        _pushTokenChangedNotifier.fire { it.onPushTokenChanged(pushToken, pushTokenStatus) }
    }

    private fun pushStatusRuntimeError(status: IPushRegistrator.RegisterStatus): Boolean {
        return status.value < -6
    }

    override fun subscribe(handler: IPushTokenChangedHandler) = _pushTokenChangedNotifier.subscribe(handler)
    override fun unsubscribe(handler: IPushTokenChangedHandler) = _pushTokenChangedNotifier.unsubscribe(handler)
}
