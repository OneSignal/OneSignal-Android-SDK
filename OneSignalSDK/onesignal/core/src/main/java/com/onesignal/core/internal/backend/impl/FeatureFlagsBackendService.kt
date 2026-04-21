package com.onesignal.core.internal.backend.impl

import com.onesignal.common.OneSignalUtils
import com.onesignal.core.internal.backend.IFeatureFlagsBackendService
import com.onesignal.core.internal.backend.RemoteFeatureFlagsFetchOutcome
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
    override suspend fun fetchRemoteFeatureFlags(appId: String): RemoteFeatureFlagsFetchOutcome {
        Logging.log(LogLevel.DEBUG, "FeatureFlagsBackendService.fetchRemoteFeatureFlags(appId=$appId)")

        val sdkVersion = OneSignalUtils.sdkVersion
        if (!isValidFeaturesSdkVersionLabel(sdkVersion)) {
            Logging.warn(
                "FeatureFlagsBackendService: sdk version not usable for Turbine path (expected " +
                    "6-digit label optional -suffix, e.g. 050801 or 050801-beta): '$sdkVersion'",
            )
            return RemoteFeatureFlagsFetchOutcome.Unavailable
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
            val msg =
                "FeatureFlagsBackendService: non-success status=${response.statusCode} body=${bodySnippet(body)}"
            // 4xx is likely a permanent misconfiguration (e.g. 403 Forbidden when the app is not
            // enrolled for Turbine feature flags) and worth surfacing at WARN; other failures are
            // typically transient (network blip, 5xx) and stay at DEBUG to avoid log spam.
            if (response.isClientError) Logging.warn(msg) else Logging.debug(msg)
            return RemoteFeatureFlagsFetchOutcome.Unavailable
        }

        val parsed = FeatureFlagsJsonParser.parseSuccessful(body)
        return if (parsed != null) {
            RemoteFeatureFlagsFetchOutcome.Success(parsed)
        } else {
            Logging.warn(
                "FeatureFlagsBackendService: response body is not valid Turbine feature-flags JSON: " +
                    bodySnippet(body),
            )
            RemoteFeatureFlagsFetchOutcome.Unavailable
        }
    }

    /**
     * Trim [body] to a short, single-line snippet safe for logcat. Caps at
     * [LOG_BODY_SNIPPET_MAX_CHARS] so we never dump large payloads into logs.
     */
    private fun bodySnippet(body: String?): String {
        if (body.isNullOrEmpty()) return "<empty>"
        val flattened = body.replace('\n', ' ').replace('\r', ' ')
        return if (flattened.length <= LOG_BODY_SNIPPET_MAX_CHARS) {
            flattened
        } else {
            flattened.take(LOG_BODY_SNIPPET_MAX_CHARS) + "…"
        }
    }

    companion object {
        /**
         * Turbine `:platform` segment for the OneSignal Android SDK (this client).
         */
        const val TURBINE_FEATURES_PLATFORM_ANDROID = "android"

        /**
         * Max chars of an HTTP response body included in diagnostic logs. Turbine error bodies
         * (e.g. `{"errors":["Forbidden"]}`) are tiny, so 200 chars is plenty and bounds worst-case
         * log size if an unexpected payload is returned.
         */
        private const val LOG_BODY_SNIPPET_MAX_CHARS = 200

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
