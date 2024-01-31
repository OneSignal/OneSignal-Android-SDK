package com.onesignal.notifications.internal.pushtoken

import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.registration.IPushRegistrator
import com.onesignal.user.internal.subscriptions.SubscriptionStatus

/**
 * The push token manager is responsible for the retrieval of the push token.
 */
internal class PushTokenManager(
    private val _pushRegistrator: IPushRegistrator,
    private val _deviceService: IDeviceService
) : IPushTokenManager {
    var pushTokenStatus: SubscriptionStatus = SubscriptionStatus.NO_PERMISSION
    var pushToken: String? = null

    override suspend fun retrievePushToken(): PushTokenResponse {
        when (_deviceService.androidSupportLibraryStatus) {
            IDeviceService.AndroidSupportLibraryStatus.MISSING -> {
                Logging.fatal("Could not find the Android Support Library. Please make sure it has been correctly added to your project.")
                pushTokenStatus = SubscriptionStatus.MISSING_ANDROID_SUPPORT_LIBRARY
            }
            IDeviceService.AndroidSupportLibraryStatus.OUTDATED -> {
                Logging.fatal("The included Android Support Library is too old or incomplete. Please update to the 26.0.0 revision or newer.")
                pushTokenStatus = SubscriptionStatus.OUTDATED_ANDROID_SUPPORT_LIBRARY
            }
            else -> {
                val registerResult = _pushRegistrator.registerForPush()

                if (registerResult.status.value == SubscriptionStatus.SUBSCRIBED.value) {
                    pushTokenStatus = registerResult.status
                } else if (registerResult.status.value < SubscriptionStatus.SUBSCRIBED.value) {
                    // Only allow errored statuses if we have never gotten a token. This ensures the
                    // device will not later be marked unsubscribed due to any inconsistencies returned
                    // by Google Play services. Also do not override a config error status if we got a
                    // runtime error
                    if (pushToken == null &&
                        (
                            pushTokenStatus == SubscriptionStatus.NO_PERMISSION ||
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

        return PushTokenResponse(pushToken, pushTokenStatus)
    }

    private fun pushStatusRuntimeError(status: SubscriptionStatus): Boolean {
        return status.value < -6
    }
}
