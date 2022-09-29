package com.onesignal.core.internal.backend.impl

import android.content.pm.PackageManager
import android.os.Build
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.backend.BackendException
import com.onesignal.core.internal.backend.ISubscriptionBackendService
import com.onesignal.core.internal.backend.SubscriptionObjectType
import com.onesignal.core.internal.common.DeviceUtils
import com.onesignal.core.internal.common.OneSignalUtils
import com.onesignal.core.internal.common.RootToolsInternalMethods
import com.onesignal.core.internal.common.TimeUtils
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.core.internal.logging.Logging
import org.json.JSONException
import org.json.JSONObject
import java.util.*

internal class SubscriptionBackendService(
    private val _application: IApplicationService,
    private val _device: IDeviceService,
    private val _http: IHttpClient
) : ISubscriptionBackendService {

    override suspend fun createSubscription(appId: String, aliasLabel: String, aliasValue: String, type: SubscriptionObjectType, enabled: Boolean, address: String): String {
        // TODO: To Implement, temporarily using players endpoint
        val json = JSONObject()
        try {
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

        if (!response.isSuccess) {
            throw BackendException(response.statusCode, response.payload)
        }

        val responseJSON = JSONObject(response.payload)
        if (!responseJSON.has("id")) {
            throw BackendException(response.statusCode, response.payload)
        }

        return responseJSON.getString("id")
    }

    override suspend fun updateSubscription(appId: String, subscriptionId: String, enabled: Boolean, address: String) {
        // TODO: To Implement
    }

    override suspend fun deleteSubscription(appId: String, subscriptionId: String) {
        // TODO: To Implement
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
