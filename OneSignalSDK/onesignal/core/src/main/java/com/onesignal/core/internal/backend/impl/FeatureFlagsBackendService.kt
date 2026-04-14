package com.onesignal.core.internal.backend.impl

import com.onesignal.common.OneSignalUtils
import com.onesignal.core.internal.backend.IFeatureFlagsBackendService
import com.onesignal.core.internal.backend.RemoteFeatureFlagsResult
import com.onesignal.core.internal.http.IHttpClient
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging

/**
 * Turbine SDK feature flags endpoint ([OneSignal/turbine#1681](https://github.com/OneSignal/turbine/pull/1681)).
 *
 * HTTP, logging, and [OneSignalUtils] are platform-specific; path shape and validation live in
 * [TurbineSdkFeatureFlagsPath], and JSON parsing in [FeatureFlagsJsonParser] (both KMP-friendly).
 *
 * **GET** `apps/{app_id}/sdk/features/{platform}/{sdk_version}` relative to
 * [com.onesignal.core.internal.config.ConfigModel.apiUrl] (app-provided base URL).
 *
 * - **platform** is always **`android`** for this SDK client.
 * - **sdk_version** is [OneSignalUtils.sdkVersion] (same label as the `SDK-Version` header segment), e.g.
 *   `050801` or `050801-beta`; see [isValidFeaturesSdkVersionLabel].
 *
 * Response: `{ "features": [ "flag_key", ... ] }`.
 */
internal class FeatureFlagsBackendService(
    private val http: IHttpClient,
) : IFeatureFlagsBackendService {
    override suspend fun fetchRemoteFeatureFlags(appId: String): RemoteFeatureFlagsResult {
        Logging.log(LogLevel.DEBUG, "FeatureFlagsBackendService.fetchRemoteFeatureFlags(appId=$appId)")

        val sdkVersion = OneSignalUtils.sdkVersion
        if (!isValidFeaturesSdkVersionLabel(sdkVersion)) {
            Logging.warn(
                "FeatureFlagsBackendService: sdk version not usable for Turbine path (expected " +
                    "6-digit label optional -suffix, e.g. 050801 or 050801-beta): '$sdkVersion'",
            )
            return RemoteFeatureFlagsResult.EMPTY
        }

        val path =
            TurbineSdkFeatureFlagsPath.buildGetPath(
                appId = appId,
                platform = TURBINE_FEATURES_PLATFORM_ANDROID,
                sdkVersion = sdkVersion,
            )

        val response = http.get(path, null)
        val body = response.payload
        if (!response.isSuccess || body.isNullOrBlank()) {
            Logging.debug(
                "FeatureFlagsBackendService: non-success or empty body, status=${response.statusCode}",
            )
            return RemoteFeatureFlagsResult.EMPTY
        }

        return FeatureFlagsJsonParser.parse(body)
    }

    companion object {
        /**
         * Turbine `:platform` segment for the OneSignal Android SDK (this client).
         */
        const val TURBINE_FEATURES_PLATFORM_ANDROID = "android"

        /**
         * Returns true when [label] is safe to send as the Turbine `:sdk_version` path segment.
         * @see TurbineSdkFeatureFlagsPath.isValidFeaturesSdkVersionLabel
         */
        fun isValidFeaturesSdkVersionLabel(label: String): Boolean = TurbineSdkFeatureFlagsPath.isValidFeaturesSdkVersionLabel(label)

        /**
         * Path only (relative to API base), matching `/apps/:app_id/sdk/features/:platform/:sdk_version`.
         * @see TurbineSdkFeatureFlagsPath.buildGetPath
         */
        internal fun buildFeatureFlagsGetPath(
            appId: String,
            platform: String,
            sdkVersion: String,
        ): String = TurbineSdkFeatureFlagsPath.buildGetPath(appId, platform, sdkVersion)
    }
}
