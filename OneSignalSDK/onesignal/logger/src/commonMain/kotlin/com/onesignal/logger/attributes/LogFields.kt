package com.onesignal.logger.attributes

import com.onesignal.logger.ILoggerPlatformProvider
import com.onesignal.logger.internal.randomUuidString

internal fun <K, V> MutableMap<K, V>.putIfValueNotNull(key: K, value: V?): MutableMap<K, V> {
    if (value != null) {
        this[key] = value
    }
    return this
}

/**
 * Top-level / resource attributes. Included on every export and, per OTLP, attached
 * to the `resource` rather than each record. Only values that cannot change during
 * runtime belong here (they are fetched once and cached by the telemetry).
 *
 * Mirrors `OtelFieldsTopLevel` key-for-key.
 */
internal class LogFieldsTopLevel(
    private val platformProvider: ILoggerPlatformProvider,
) {
    suspend fun getAttributes(): Map<String, String> {
        val attributes: MutableMap<String, String> =
            mutableMapOf(
                "ossdk.install_id" to platformProvider.getInstallId(),
                "ossdk.sdk_base" to platformProvider.sdkBase,
                "ossdk.sdk_base_version" to platformProvider.sdkBaseVersion,
                "ossdk.app_package_id" to platformProvider.appPackageId,
                "ossdk.app_version" to platformProvider.appVersion,
                "device.manufacturer" to platformProvider.deviceManufacturer,
                "device.model.identifier" to platformProvider.deviceModel,
                "os.name" to platformProvider.osName,
                "os.version" to platformProvider.osVersion,
                "os.build_id" to platformProvider.osBuildId,
            )

        attributes
            .putIfValueNotNull("ossdk.sdk_wrapper", platformProvider.sdkWrapper)
            .putIfValueNotNull("ossdk.sdk_wrapper_version", platformProvider.sdkWrapperVersion)

        return attributes.toMap()
    }
}

/**
 * Per-event attributes. Recomputed for every record so each one reflects the current
 * state (IDs, app state, enabled feature flags, etc.).
 *
 * Mirrors `OtelFieldsPerEvent` key-for-key.
 */
internal class LogFieldsPerEvent(
    private val platformProvider: ILoggerPlatformProvider,
) {
    fun getAttributes(): Map<String, String> {
        val attributes: MutableMap<String, String> = mutableMapOf()

        attributes["log.record.uid"] = randomUuidString()

        attributes
            .putIfValueNotNull("ossdk.app_id", platformProvider.appId)
            .putIfValueNotNull("ossdk.onesignal_id", platformProvider.onesignalId)
            .putIfValueNotNull("ossdk.push_subscription_id", platformProvider.pushSubscriptionId)

        attributes["app.state"] = platformProvider.appState
        attributes["process.uptime"] = platformProvider.processUptime.toString()
        attributes["thread.name"] = platformProvider.currentThreadName

        val enabledFlags = platformProvider.enabledFeatureFlags
        if (enabledFlags.isNotEmpty()) {
            attributes["ossdk.feature_flags"] = enabledFlags.sorted().joinToString(",")
        }

        return attributes.toMap()
    }
}
