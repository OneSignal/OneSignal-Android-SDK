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

/**
 * Android host for the shared Turbine feature-flags client.
 *
 * This class is the platform adapter iOS should mirror: wrap native HTTP as
 * [IFeatureFlagsHttp], construct [FeatureFlagsClient], log [Unavailable] at the
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
        if (outcome is RemoteFeatureFlagsFetchOutcome.Unavailable) {
            logUnavailable(outcome, sdkVersion)
        }
        return outcome
    }

    private fun logUnavailable(
        outcome: RemoteFeatureFlagsFetchOutcome.Unavailable,
        sdkVersion: String,
    ) {
        when (outcome.reason) {
            RemoteFeatureFlagsFetchOutcome.Unavailable.Reason.INVALID_SDK_VERSION ->
                Logging.warn(
                    "FeatureFlagsBackendService: sdk version not usable for Turbine path (expected " +
                        "6-digit label optional -suffix, e.g. 050801 or 050801-beta): '$sdkVersion'",
                )
            RemoteFeatureFlagsFetchOutcome.Unavailable.Reason.NON_SUCCESS_HTTP -> {
                val msg =
                    "FeatureFlagsBackendService: non-success status=${outcome.statusCode} " +
                        "body=${outcome.bodySnippet}"
                val status = outcome.statusCode
                if (status != null && status in 400 until 500) {
                    Logging.warn(msg)
                } else {
                    Logging.debug(msg)
                }
            }
            RemoteFeatureFlagsFetchOutcome.Unavailable.Reason.EMPTY_BODY ->
                Logging.warn(
                    "FeatureFlagsBackendService: empty body for success status=${outcome.statusCode}",
                )
            RemoteFeatureFlagsFetchOutcome.Unavailable.Reason.INVALID_JSON ->
                Logging.warn(
                    "FeatureFlagsBackendService: response body is not valid Turbine feature-flags JSON: " +
                        outcome.bodySnippet,
                )
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
