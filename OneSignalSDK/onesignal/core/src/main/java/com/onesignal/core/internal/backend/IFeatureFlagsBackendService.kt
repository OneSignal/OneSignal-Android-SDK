package com.onesignal.core.internal.backend

import kotlinx.serialization.json.JsonObject

/**
 * Result of the dedicated SDK feature-flags endpoint (separate from [IParamsBackendService]).
 *
 * @param enabledKeys Feature keys that should be treated as enabled for this device/SDK.
 * @param metadata Optional per-flag payload (e.g. weights), keyed by flag id. Parsed from sibling
 * keys in the response JSON (see [com.onesignal.core.internal.backend.impl.FeatureFlagsJsonParser]).
 */
internal data class RemoteFeatureFlagsResult(
    val enabledKeys: List<String>,
    val metadata: JsonObject?,
) {
    companion object {
        val EMPTY = RemoteFeatureFlagsResult(emptyList(), null)
    }
}

/**
 * Outcome of [IFeatureFlagsBackendService.fetchRemoteFeatureFlags].
 * [Unavailable] means the client did not get a trustworthy response (HTTP error, invalid body, etc.);
 * callers should keep previously cached flags. [Success] includes a valid HTTP parse, including an
 * empty `features` array from the server.
 */
internal sealed class RemoteFeatureFlagsFetchOutcome {
    data class Success(val result: RemoteFeatureFlagsResult) : RemoteFeatureFlagsFetchOutcome()

    data object Unavailable : RemoteFeatureFlagsFetchOutcome()
}

/**
 * Fetches remote feature flags for the current app via **GET**
 * `apps/{app_id}/sdk/features/{platform}/{sdk_version}` (Turbine; see
 * [com.onesignal.core.internal.backend.impl.FeatureFlagsBackendService]).
 */
internal interface IFeatureFlagsBackendService {
    suspend fun fetchRemoteFeatureFlags(appId: String): RemoteFeatureFlagsFetchOutcome
}
