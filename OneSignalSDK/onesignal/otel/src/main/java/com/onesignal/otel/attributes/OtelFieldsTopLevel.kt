package com.onesignal.otel.attributes

import com.onesignal.otel.IOtelPlatformProvider
import com.squareup.wire.internal.toUnmodifiableMap

/**
 * Purpose: Fields to be included in every Otel request that goes out.
 * Requirements: Only include fields that can NOT change during runtime,
 * as these are only fetched once. (Calculated fields are ok)
 */
internal class OtelFieldsTopLevel(
    private val platformProvider: IOtelPlatformProvider,
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

        return attributes.toUnmodifiableMap()
    }
}

internal fun <K, V> MutableMap<K, V>.putIfValueNotNull(key: K, value: V?): MutableMap<K, V> {
    if (value != null) {
        this[key] = value
    }
    return this
}
