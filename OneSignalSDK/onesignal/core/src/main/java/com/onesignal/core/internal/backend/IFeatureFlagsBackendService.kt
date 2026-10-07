package com.onesignal.core.internal.backend

import com.onesignal.features.RemoteFeatureFlagsFetchOutcome

/**
 * Fetches remote feature flags for the current app via the shared KMP
 * [com.onesignal.features.FeatureFlagsClient] (Turbine GET
 * `apps/{app_id}/sdk/features/{platform}/{sdk_version}`).
 *
 * iOS hosts the same client: implement [com.onesignal.features.IFeatureFlagsHttp]
 * with URLSession, pass platform `"ios"`.
 */
internal interface IFeatureFlagsBackendService {
    suspend fun fetchRemoteFeatureFlags(appId: String): RemoteFeatureFlagsFetchOutcome
}
