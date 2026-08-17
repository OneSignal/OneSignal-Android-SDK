package com.onesignal.core.internal.backend.impl

import com.onesignal.common.OneSignalUtils
import com.onesignal.core.internal.backend.IFeatureFlagsBackendService
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.features.FeatureFlagsClient
import com.onesignal.features.FeatureFlagsHttpResponse
import com.onesignal.features.IFeatureFlagsHttp
import com.onesignal.features.RemoteFeatureFlagsFetchOutcome
import com.onesignal.features.RemoteFeatureFlagsUnavailableReason

/**
 * Android host for the shared Turbine feature-flags client.
 *
 * This class is the platform adapter iOS should mirror: wrap native HTTP as
 * [IFeatureFlagsHttp], construct [FeatureFlagsClient], log unavailable outcomes at the
 * right severity, and leave path/parse/orchestration in KMP.
 */
internal class FeatureFlagsBackendService(
    http: IHttpClient,
) : IFeatureFlagsBackendService {
    private val client = FeatureFlagsClient(FeatureFlagsHttpAdapter(http))

    override suspend fun fetchRemoteFeatureFlags(appId: String): RemoteFeatureFlagsFetchOutcome {
        Logging.log(LogLevel.DEBUG, "FeatureFlagsBackendService.fetchRemoteFeatureFlags(appId=$appId)")

        val sdkVersion = OneSignalUtils.sdkVersion
        val outcome =
            client.fetchRemoteFeatureFlags(
                appId = appId,
                platform = TURBINE_FEATURES_PLATFORM_ANDROID,
                sdkVersion = sdkVersion,
            )
        if (outcome.isUnavailable) {
            logUnavailable(outcome, appId, sdkVersion)
        }
        return outcome
    }

    private fun logUnavailable(
        outcome: RemoteFeatureFlagsFetchOutcome,
        appId: String,
        sdkVersion: String,
    ) {
        when (outcome.reason) {
            RemoteFeatureFlagsUnavailableReason.INVALID_APP_ID ->
                Logging.warn(
                    "FeatureFlagsBackendService: app id not usable for Turbine path: '$appId'",
                )
            RemoteFeatureFlagsUnavailableReason.INVALID_SDK_VERSION ->
                Logging.warn(
                    "FeatureFlagsBackendService: sdk version not usable for Turbine path (expected " +
                        "6-digit label optional -suffix, e.g. 050801 or 050801-beta): '$sdkVersion'",
                )
            RemoteFeatureFlagsUnavailableReason.NON_SUCCESS_HTTP -> {
                val msg =
                    "FeatureFlagsBackendService: non-success status=${outcome.statusCode} " +
                        "body=${outcome.bodySnippet}"
                if (outcome.isClientError) {
                    Logging.warn(msg)
                } else {
                    Logging.debug(msg)
                }
            }
            RemoteFeatureFlagsUnavailableReason.EMPTY_BODY ->
                Logging.warn(
                    "FeatureFlagsBackendService: empty body for success status=${outcome.statusCode}",
                )
            RemoteFeatureFlagsUnavailableReason.INVALID_JSON ->
                Logging.warn(
                    "FeatureFlagsBackendService: response body is not valid Turbine feature-flags JSON: " +
                        outcome.bodySnippet,
                )
            null ->
                Logging.warn("FeatureFlagsBackendService: unavailable without reason")
        }
    }

    companion object {
        const val TURBINE_FEATURES_PLATFORM_ANDROID = "android"
    }
}

private class FeatureFlagsHttpAdapter(
    private val http: IHttpClient,
) : IFeatureFlagsHttp {
    override suspend fun get(relativePath: String): FeatureFlagsHttpResponse {
        val response = http.get(relativePath, null)
        return FeatureFlagsHttpResponse(response.statusCode, response.payload)
    }
}
