package com.onesignal.onesignal.core.internal.operations.executors

import android.content.pm.PackageManager
import android.os.Build
import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.backend.http.IHttpClient
import com.onesignal.onesignal.core.internal.common.DeviceUtils
import com.onesignal.onesignal.core.internal.common.OneSignalUtils
import com.onesignal.onesignal.core.internal.common.RootToolsInternalMethods
import com.onesignal.onesignal.core.internal.common.TimeUtils
import com.onesignal.onesignal.core.internal.device.IDeviceService
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.operations.CreateAndAddSubscriptionOperation
import com.onesignal.onesignal.core.internal.operations.CreateSubscriptionOperation
import com.onesignal.onesignal.core.internal.operations.DeleteSubscriptionOperation
import com.onesignal.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.onesignal.core.internal.operations.Operation
import com.onesignal.onesignal.core.internal.operations.UpdateSubscriptionOperation
import com.onesignal.onesignal.core.internal.user.ISubscriptionManager
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

internal class SubscriptionOperationExecutor(
    private val _application: IApplicationService,
    private val _device: IDeviceService,
    private val _subscriptionManager: ISubscriptionManager,
    private val _http: IHttpClient
) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(CREATE_SUBSCRIPTION, CREATE_AND_ADD_SUBSCRIPTION, UPDATE_SUBSCRIPTION, DELETE_SUBSCRIPTION)

    override suspend fun executeAsync(operation: Operation) {
        Logging.log(LogLevel.DEBUG, "SubscriptionOperationExecutor(operation: $operation)")

        when (operation) {
            is CreateSubscriptionOperation -> {
                createSubscription(operation.appId, operation.address, operation.id)
            }
            is CreateAndAddSubscriptionOperation -> {
                createSubscription(operation.appId, operation.address, null)
            }
            is DeleteSubscriptionOperation -> {
//                _api.deleteSubscriptionAsync(operation.id)
            }
            is UpdateSubscriptionOperation -> {
//                _api.updateSubscriptionAsync()
            }
        }
    }

    private suspend fun createSubscription(appId: String, address: String, id: String?) {
        val json = JSONObject()
        try {
            // app_id required for all REST API calls

            // app_id required for all REST API calls
            json.put("app_id", appId)
            json.put("device_type", _device.deviceType)
            json.put("device_model", Build.MODEL)
            json.put("device_os", Build.VERSION.RELEASE)
            json.put("timezone", TimeUtils.getTimeZoneOffset())
            json.put("timezone_id", TimeUtils.getTimeZoneId())
            json.put("language", getLanguage())
            json.put("sdk", OneSignalUtils.sdkVersion)
            json.put("sdk_type", OneSignalUtils.sdkType)
            json.put("android_package", _application.appContext.packageName)

            try {
                json.put("game_version", _application.appContext.packageManager.getPackageInfo(_application.appContext.packageName, 0).versionCode)
            } catch (e: PackageManager.NameNotFoundException) {
            }

            json.put("net_type", DeviceUtils.getNetType(_application.appContext))
            json.put("carrier", DeviceUtils.getCarrierName(_application.appContext))
            json.put("rooted", RootToolsInternalMethods.isRooted)

            json.put("identifier", address)
        } catch (e: JSONException) {
            Logging.error("Could not create JSON payload for create subscription", e)
        }

        val response = _http.post("players", json)
        if (response.isSuccess) {
            val responseJSON = JSONObject(response.payload)
            if (responseJSON.has("id")) {
                val subscriptionId: String = responseJSON.optString("id")
                Logging.info("Device registered, SubscriptionId = $subscriptionId")

                if (id == null) {
                    _subscriptionManager.addPushSubscription(subscriptionId, address)
                } else {
                    // TODO: Update the subscription ID to use the server side one. Do we need this?
                }
            }
        } else {
            Logging.warn("Failed last request. statusCode: $response.statusCode\nresponse: $response")

            // TODO: Need a concept of retrying on network error, and other error handling
//            if (response400WithErrorsContaining(response.statusCode, response.payload, "not a valid device_type"))
//                handlePlayerDeletedFromServer()
//            else
//                handleNetworkFailure(response.statusCode)
        }
    }

    // TODO: Temporary placement until language services hooked back in
    private fun getLanguage(): String? {
        val language = Locale.getDefault().language
        return when (language) {
            HEBREW_INCORRECT -> HEBREW_CORRECTED
            INDONESIAN_INCORRECT -> INDONESIAN_CORRECTED
            YIDDISH_INCORRECT -> YIDDISH_CORRECTED
            CHINESE -> language + "-" + Locale.getDefault().country
            else -> language
        }
    }

    companion object {
        const val CREATE_SUBSCRIPTION = "create-subscription"
        const val CREATE_AND_ADD_SUBSCRIPTION = "create-and-add-subscription"
        const val UPDATE_SUBSCRIPTION = "update-subscription"
        const val DELETE_SUBSCRIPTION = "delete-subscription"

        private const val ERRORS = "errors"
        private const val HEBREW_INCORRECT = "iw"
        private const val HEBREW_CORRECTED = "he"
        private const val INDONESIAN_INCORRECT = "in"
        private const val INDONESIAN_CORRECTED = "id"
        private const val YIDDISH_INCORRECT = "ji"
        private const val YIDDISH_CORRECTED = "yi"
        private const val CHINESE = "zh"
    }
}
