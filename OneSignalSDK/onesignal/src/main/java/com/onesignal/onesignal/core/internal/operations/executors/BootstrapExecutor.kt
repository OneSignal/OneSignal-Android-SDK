package com.onesignal.onesignal.core.internal.operations.executors

import com.onesignal.onesignal.core.internal.backend.http.CacheKeys
import com.onesignal.onesignal.core.internal.backend.http.IHttpClient
import com.onesignal.onesignal.core.LogLevel
import com.onesignal.onesignal.core.internal.logging.Logging
import com.onesignal.onesignal.core.internal.operations.BootstrapOperation
import com.onesignal.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.onesignal.core.internal.operations.Operation
import com.onesignal.onesignal.core.internal.params.IParamsService
import com.onesignal.onesignal.core.internal.params.IWriteableParamsService
import com.onesignal.onesignal.core.internal.service.IStartableService
import kotlinx.coroutines.delay
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection

class BootstrapExecutor(
    private val _writeableParams: IWriteableParamsService,
    private val _startableServices: List<IStartableService>,
    private val _http: IHttpClient) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(BOOTSTRAP)

    override suspend fun executeAsync(operation: Operation) {
        Logging.log(LogLevel.DEBUG, "PropertyOperationExecutor(operation: $operation)")

        if(!(operation is BootstrapOperation))
            throw Exception("BootstrapExecutor is expecting BootstrapOperation, received $operation")

        var params_url = "apps/${operation.appId}/android_params.js"
        if (operation.subscriptionId != null)
            params_url += "?player_id=${operation.subscriptionId}"

        // retrieve the params in a do-while loop.  If the first call fails we want to retry
        // until we get it right.  The only time we quit on failure is if there's an exception,
        // in which case there's some fatal error and things need to shut down.
        var androidParamsRetries = 0
        do {
            Logging.debug("Starting request to get Android parameters.")
            val response = _http.get(params_url, CacheKeys.REMOTE_PARAMS)

            if(response.isSuccess)
                processJson(operation.appId, response.payload!!)
            else if (response.statusCode == HttpURLConnection.HTTP_FORBIDDEN) {
                Logging.fatal("403 error getting OneSignal params, omitting further retries!")
                return
            }
            else {
                var sleepTime = MIN_WAIT_BETWEEN_RETRIES + androidParamsRetries * INCREASE_BETWEEN_RETRIES
                if (sleepTime > MAX_WAIT_BETWEEN_RETRIES)
                    sleepTime = MAX_WAIT_BETWEEN_RETRIES

                Logging.info("Failed to get Android parameters, trying again in " + sleepTime / 1000 + " seconds.")

                delay(sleepTime.toLong())
                androidParamsRetries++
            }
        } while (!response.isSuccess)

        // now that we have the params initialized, start everything else up
        for(startableService in _startableServices)
            startableService.start()
    }

    private fun processJson(appId: String, json: String) {
        val responseJson: JSONObject = try {
            JSONObject(json)
        } catch (t: NullPointerException) {
            Logging.fatal("Error parsing android_params!: ", t)
            Logging.fatal("Response that errored from android_params!: $json")
            return
        } catch (t: JSONException) {
            Logging.fatal("Error parsing android_params!: ", t)
            Logging.fatal("Response that errored from android_params!: $json")
            return
        }

        _writeableParams.appId = appId
        _writeableParams.enterprise = responseJson.optBoolean("enterp", false)
        _writeableParams.useEmailAuth = responseJson.optBoolean("require_email_auth", false)
        _writeableParams.useUserIdAuth = responseJson.optBoolean("require_user_id_auth", false)
        _writeableParams.notificationChannels = responseJson.optJSONArray("chnl_lst")
        _writeableParams.firebaseAnalytics = responseJson.optBoolean("fba", false)
        _writeableParams.restoreTTLFilter = responseJson.optBoolean("restore_ttl_filter", true)
        _writeableParams.googleProjectNumber = responseJson.optString("android_sender_id", null)
        _writeableParams.clearGroupOnSummaryClick =
            responseJson.optBoolean("clear_group_on_summary_click", true)
        _writeableParams.receiveReceiptEnabled =
            responseJson.optBoolean("receive_receipts_enable", false)

        // Null assignation to avoid remote param override user configuration until backend is done
        // TODO remove the has check when backend has new remote params and sets inside OneSignal.java are removed
        _writeableParams.disableGMSMissingPrompt =
            if (!responseJson.has(DISABLE_GMS_MISSING_PROMPT)) null else responseJson.optBoolean(
                DISABLE_GMS_MISSING_PROMPT,
                false
            )
        _writeableParams.unsubscribeWhenNotificationsDisabled =
            if (!responseJson.has(UNSUBSCRIBE_ON_NOTIFICATION_DISABLE)) null else responseJson.optBoolean(
                UNSUBSCRIBE_ON_NOTIFICATION_DISABLE,
                true
            )
        _writeableParams.locationShared =
            if (!responseJson.has(LOCATION_SHARED)) null else responseJson.optBoolean(
                LOCATION_SHARED,
                true
            )
        _writeableParams.requiresUserPrivacyConsent =
            if (!responseJson.has(REQUIRES_USER_PRIVACY_CONSENT)) null else responseJson.optBoolean(
                REQUIRES_USER_PRIVACY_CONSENT,
                false
            )

        // Process outcomes params
        if (responseJson.has(OUTCOME_PARAM))
            _writeableParams.influenceParams = processOutcomeJson(responseJson.optJSONObject(OUTCOME_PARAM))
        else
            _writeableParams.influenceParams = IParamsService.InfluenceParams()


        if (responseJson.has(FCM_PARENT_PARAM)) {
            var projectId: String?
            var appId: String?
            var apiKey: String?

            val fcm = responseJson.optJSONObject(FCM_PARENT_PARAM)
            apiKey = fcm.optString(FCM_API_KEY, null)
            appId = fcm.optString(FCM_APP_ID, null)
            projectId = fcm.optString(FCM_PROJECT_ID, null)

            _writeableParams.fcmParams = IParamsService.FCMParams(projectId, appId, apiKey)
        }
        else {
            _writeableParams.fcmParams = IParamsService.FCMParams()
        }
    }

    private fun processOutcomeJson(outcomeJson: JSONObject) : IParamsService.InfluenceParams {
        var indirectNotificationAttributionWindow: Int = IParamsService.InfluenceParams.DEFAULT_INDIRECT_ATTRIBUTION_WINDOW
        var notificationLimit: Int = IParamsService.InfluenceParams.DEFAULT_NOTIFICATION_LIMIT
        var indirectIAMAttributionWindow: Int = IParamsService.InfluenceParams.DEFAULT_INDIRECT_ATTRIBUTION_WINDOW
        var iamLimit: Int = IParamsService.InfluenceParams.DEFAULT_NOTIFICATION_LIMIT
        var isDirectEnabled: Boolean = false
        var isIndirectEnabled: Boolean = false
        var isUnattributedEnabled: Boolean = false
        var outcomesV2ServiceEnabled: Boolean = false

        if (outcomeJson.has(OUTCOMES_V2_SERVICE_PARAM))
            outcomesV2ServiceEnabled = outcomeJson.optBoolean(OUTCOMES_V2_SERVICE_PARAM)
        if (outcomeJson.has(DIRECT_PARAM)) {
            val direct = outcomeJson.optJSONObject(DIRECT_PARAM)
            isDirectEnabled = direct.optBoolean(ENABLED_PARAM)
        }

        if (outcomeJson.has(INDIRECT_PARAM)) {
            val indirect = outcomeJson.optJSONObject(INDIRECT_PARAM)!!
            isIndirectEnabled = indirect.optBoolean(ENABLED_PARAM)
            if (indirect.has(NOTIFICATION_ATTRIBUTION_PARAM)) {
                val indirectNotificationAttribution = indirect.optJSONObject(NOTIFICATION_ATTRIBUTION_PARAM)!!
                indirectNotificationAttributionWindow = indirectNotificationAttribution.optInt(
                        "minutes_since_displayed", IParamsService.InfluenceParams.DEFAULT_INDIRECT_ATTRIBUTION_WINDOW)
                notificationLimit = indirectNotificationAttribution.optInt("limit", IParamsService.InfluenceParams.DEFAULT_NOTIFICATION_LIMIT)
            }

            if (indirect.has(IAM_ATTRIBUTION_PARAM)) {
                val indirectIAMAttribution = indirect.optJSONObject(IAM_ATTRIBUTION_PARAM)!!
                indirectIAMAttributionWindow = indirectIAMAttribution.optInt("minutes_since_displayed", IParamsService.InfluenceParams.DEFAULT_INDIRECT_ATTRIBUTION_WINDOW)

                if (indirectIAMAttribution != null) {
                    iamLimit = indirectIAMAttribution.optInt("limit", IParamsService.InfluenceParams.DEFAULT_NOTIFICATION_LIMIT)
                }
            }
        }

        if (outcomeJson.has(UNATTRIBUTED_PARAM)) {
            val unattributed = outcomeJson.optJSONObject(UNATTRIBUTED_PARAM)!!
            isUnattributedEnabled = unattributed.optBoolean(ENABLED_PARAM)
        }

        return IParamsService.InfluenceParams(
            indirectNotificationAttributionWindow,
            notificationLimit,
            indirectIAMAttributionWindow,
            iamLimit,
            isDirectEnabled,
            isIndirectEnabled,
            isUnattributedEnabled,
            outcomesV2ServiceEnabled,
        )
    }

    companion object {
        const val BOOTSTRAP = "bootstrap"

        private const val INCREASE_BETWEEN_RETRIES = 10000
        private const val MIN_WAIT_BETWEEN_RETRIES = 30000
        private const val MAX_WAIT_BETWEEN_RETRIES = 90000

        private const val OUTCOME_PARAM = "outcomes"
        private const val OUTCOMES_V2_SERVICE_PARAM = "v2_enabled"
        private const val ENABLED_PARAM = "enabled"
        private const val DIRECT_PARAM = "direct"
        private const val INDIRECT_PARAM = "indirect"
        private const val NOTIFICATION_ATTRIBUTION_PARAM = "notification_attribution"
        private const val IAM_ATTRIBUTION_PARAM = "in_app_message_attribution"
        private const val UNATTRIBUTED_PARAM = "unattributed"

        private const val UNSUBSCRIBE_ON_NOTIFICATION_DISABLE =
            "unsubscribe_on_notifications_disabled"
        private const val DISABLE_GMS_MISSING_PROMPT = "disable_gms_missing_prompt"
        private const val LOCATION_SHARED = "location_shared"
        private const val REQUIRES_USER_PRIVACY_CONSENT = "requires_user_privacy_consent"

        private const val FCM_PARENT_PARAM = "fcm"
        private const val FCM_PROJECT_ID = "project_id"
        private const val FCM_APP_ID = "app_id"
        private const val FCM_API_KEY = "api_key"
    }
}