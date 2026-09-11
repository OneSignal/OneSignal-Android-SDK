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
    private val _deviceService: IDeviceService,
) : IPushTokenManager {
    var pushTokenStatus: SubscriptionStatus = SubscriptionStatus.NO_PERMISSION
    var pushToken: String? = null

    override suspend fun retrievePushToken(): PushTokenResponse {
        when (_deviceService.jetpackLibraryStatus) {
            IDeviceService.JetpackLibraryStatus.MISSING -> {
                Logging.info("Could not find the Jetpack/AndroidX. Please make sure it has been correctly added to your project.")
                pushTokenStatus = SubscriptionStatus.MISSING_JETPACK_LIBRARY
            }
            IDeviceService.JetpackLibraryStatus.OUTDATED -> {
                Logging.info(
                    "The included Jetpack/AndroidX Library is too old or incomplete.",
                )
                pushTokenStatus = SubscriptionStatus.OUTDATED_JETPACK_LIBRARY
            }
            else -> {
                val registerResult = _pushRegistrator.registerForPush()

                val shouldUpdate =
                    when {
                        registerResult.status.value == SubscriptionStatus.SUBSCRIBED.value -> true
                        registerResult.status.value < SubscriptionStatus.SUBSCRIBED.value ->
                            shouldUpdateErrorStatus(registerResult)
                        else -> pushTokenStatus.isRetryableTokenError
                    }

                if (shouldUpdate) {
                    pushTokenStatus = registerResult.status
                    pushToken = registerResult.id
                }
            }
        }

        return PushTokenResponse(pushToken, pushTokenStatus)
    }

    private fun shouldUpdateErrorStatus(registerResult: IPushRegistrator.RegisterResult): Boolean =
        when {
            registerResult.isExistingTokenInvalid -> true
            pushToken != null -> false
            !registerResult.status.isRetryableTokenError -> true
            else -> pushTokenStatus == SubscriptionStatus.NO_PERMISSION || pushTokenStatus.isRetryableTokenError
        }
}
