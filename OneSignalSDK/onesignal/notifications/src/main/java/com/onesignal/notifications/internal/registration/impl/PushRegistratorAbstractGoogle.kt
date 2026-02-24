/**
 * Modified MIT License
 *
 * Copyright 2018 OneSignal
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * 1. The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * 2. All copies of substantial portions of the Software may only be used in connection
 * with services provided by OneSignal.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.onesignal.notifications.internal.registration.impl

import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.notifications.internal.registration.IPushRegistrator
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import kotlinx.coroutines.delay
import java.io.IOException
import java.util.concurrent.ExecutionException

/**
 * The abstract google push registration service.  It is expected [PushRegistratorFCM] will extend
 * this class.  This performs error handling and retry logic for FCM.
 */
internal abstract class PushRegistratorAbstractGoogle(
    private val _deviceService: IDeviceService,
    private var _configModelStore: ConfigModelStore,
    private val _upgradePrompt: GooglePlayServicesUpgradePrompt,
) :
    IPushRegistrator, IPushRegistratorCallback {
    abstract val providerName: String

    @Throws(ExecutionException::class, InterruptedException::class, IOException::class)
    abstract suspend fun getToken(senderId: String): String

    override suspend fun registerForPush(): IPushRegistrator.RegisterResult {
        if (!_configModelStore.model.isInitializedWithRemote) {
            return IPushRegistrator.RegisterResult(null, SubscriptionStatus.FIREBASE_FCM_INIT_ERROR)
        }

        if (!_deviceService.hasFCMLibrary) {
            Logging.warn("The Firebase FCM library is missing! Please make sure to include it in your project.")
            return IPushRegistrator.RegisterResult(null, SubscriptionStatus.MISSING_FIREBASE_FCM_LIBRARY)
        }

        return if (!isValidProjectNumber(_configModelStore.model.googleProjectNumber)) {
            Logging.warn(
                "Missing Google Project number!\nPlease enter a Google Project number / Sender ID on under App Settings > Android > Configuration on the OneSignal dashboard.",
            )
            IPushRegistrator.RegisterResult(
                null,
                SubscriptionStatus.INVALID_FCM_SENDER_ID,
            )
        } else {
            internalRegisterForPush(_configModelStore.model.googleProjectNumber!!)
        }
    }

    override suspend fun fireCallback(id: String?) {
        throw Exception("Google has no callback mechanism for push registration!")
    }

    private suspend fun internalRegisterForPush(senderId: String): IPushRegistrator.RegisterResult {
        try {
            return if (_deviceService.isGMSInstalledAndEnabled) {
                registerInBackground(senderId)
            } else {
                _upgradePrompt.showUpdateGPSDialog()
                Logging.warn("'Google Play services' app not installed or disabled on the device.")
                IPushRegistrator.RegisterResult(
                    null,
                    SubscriptionStatus.OUTDATED_GOOGLE_PLAY_SERVICES_APP,
                )
            }
        } catch (t: Throwable) {
            Logging.warn(
                "Could not register with $providerName due to an issue with your AndroidManifest.xml or with 'Google Play services'.",
                t,
            )
        }

        return IPushRegistrator.RegisterResult(
            null,
            SubscriptionStatus.FIREBASE_FCM_INIT_ERROR,
        )
    }

    private suspend fun registerInBackground(senderId: String): IPushRegistrator.RegisterResult {
        for (currentRetry in 0 until REGISTRATION_RETRY_COUNT) {
            val result = attemptRegistration(senderId, currentRetry)
            if (result != null) {
                return result
            }

            delay((REGISTRATION_RETRY_BACKOFF_MS * (currentRetry + 1)).toLong())
        }

        // TODO: New error?
        return IPushRegistrator.RegisterResult(
            null,
            SubscriptionStatus.FIREBASE_FCM_INIT_ERROR,
        )
    }

    private suspend fun attemptRegistration(
        senderId: String,
        currentRetry: Int,
    ): IPushRegistrator.RegisterResult? {
        try {
            val registrationId = getToken(senderId)
            Logging.info("Device registered, push token = $registrationId")
            return IPushRegistrator.RegisterResult(
                registrationId,
                SubscriptionStatus.SUBSCRIBED,
            )
        } catch (e: IOException) {
            val pushStatus: SubscriptionStatus = pushStatusFromThrowable(e)
            val exceptionMessage: String? = AndroidUtils.getRootCauseMessage(e)
            val retryingKnownToWorkSometimes = "SERVICE_NOT_AVAILABLE" == exceptionMessage || "AUTHENTICATION_FAILED" == exceptionMessage

            if (retryingKnownToWorkSometimes) {
                // Wrapping with new Exception so the current line is included in the stack trace.
                val exception = Exception(e)
                if (currentRetry >= REGISTRATION_RETRY_COUNT - 1) {
                    Logging.info("Retry count of $REGISTRATION_RETRY_COUNT exceed! Could not get a $providerName Token.", exception)
                } else {
                    Logging.info("'Google Play services' returned $exceptionMessage error. Current retry count: $currentRetry", exception)

                    if (currentRetry === 2) {
                        // Retry 3 times before firing a null response and continuing a few more times.
                        return IPushRegistrator.RegisterResult(null, pushStatus)
                    }
                }
            } else {
                // Wrapping with new Exception so the current line is included in the stack trace.
                val exception = Exception(e)
                Logging.warn("Error Getting $providerName Token", exception)

                return IPushRegistrator.RegisterResult(null, pushStatus)
            }
        } catch (t: Throwable) {
            Logging.warn("Unknown error getting $providerName Token", t)
            return IPushRegistrator.RegisterResult(
                null,
                SubscriptionStatus.FIREBASE_FCM_ERROR_MISC_EXCEPTION,
            )
        }

        return null
    }

    private fun pushStatusFromThrowable(throwable: Throwable): SubscriptionStatus {
        val exceptionMessage: String? = AndroidUtils.getRootCauseMessage(throwable)
        return if (throwable is IOException) {
            when (exceptionMessage) {
                "SERVICE_NOT_AVAILABLE" -> SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_SERVICE_NOT_AVAILABLE
                "AUTHENTICATION_FAILED" -> SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_AUTHENTICATION_FAILED
                else -> SubscriptionStatus.FIREBASE_FCM_ERROR_IOEXCEPTION_OTHER
            }
        } else {
            SubscriptionStatus.FIREBASE_FCM_ERROR_MISC_EXCEPTION
        }
    }

    private fun isValidProjectNumber(senderId: String?): Boolean {
        val isProjectNumberValidFormat: Boolean =
            try {
                senderId!!.toFloat()
                true
            } catch (t: Throwable) {
                false
            }

        if (!isProjectNumberValidFormat) {
            return false
        }

        return true
    }

    companion object {
        private const val REGISTRATION_RETRY_COUNT = 5
        private const val REGISTRATION_RETRY_BACKOFF_MS = 10000
    }
}
