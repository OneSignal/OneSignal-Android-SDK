package com.onesignal.otel.attributes

import com.onesignal.otel.IOtelPlatformProvider
import com.squareup.wire.internal.toUnmodifiableMap
import java.util.UUID

/**
 * Purpose: Fields to be included in each individual Otel event.
 * These can change during runtime.
 */
internal class OtelFieldsPerEvent(
    private val platformProvider: IOtelPlatformProvider,
) {
    fun getAttributes(): Map<String, String> {
        val attributes: MutableMap<String, String> = mutableMapOf()

        attributes["log.record.uid"] = recordId.toString()

        attributes
            .putIfValueNotNull("ossdk.app_id", platformProvider.appId)
            .putIfValueNotNull("ossdk.onesignal_id", platformProvider.onesignalId)
            .putIfValueNotNull("ossdk.push_subscription_id", platformProvider.pushSubscriptionId)

        // Use platform-agnostic attribute name (works for both Android and iOS)
        attributes["app.state"] = platformProvider.appState
        attributes["process.uptime"] = platformProvider.processUptime.toString()
        attributes["thread.name"] = platformProvider.currentThreadName

        // Encode the currently-enabled feature flag keys as a single sorted, comma-separated
        // string. Read fresh on every emission so each record reflects the FeatureManager view
        // at the moment the log was written — no SDK rebuild required for IMMEDIATE-mode flag
        // changes. Easily queryable in Google Cloud Logs Explorer via the `:` (contains)
        // operator; omitted entirely when no flags are enabled to keep payloads compact.
        val enabledFlags = platformProvider.enabledFeatureFlags
        if (enabledFlags.isNotEmpty()) {
            attributes["ossdk.feature_flags"] = enabledFlags.sorted().joinToString(",")
        }

        return attributes.toUnmodifiableMap()
    }

    // idempotency so the backend can filter on duplicate events
    // https://opentelemetry.io/docs/specs/semconv/general/logs/#general-log-identification-attributes
    private val recordId: UUID get() = UUID.randomUUID()
}
