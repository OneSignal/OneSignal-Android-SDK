package com.onesignal.otel.attributes

import com.onesignal.otel.IOtelPlatformProvider
import com.squareup.wire.internal.toUnmodifiableMap

/**
 * Purpose: Fields to be included in every Otel request that goes out.
 * Requirements: Only include fields that can NOT change during runtime,
 * as these are only fetched once. (Calculated fields are ok)
 *
 * Optional host language / toolchain versions (`ossdk.kotlin_version`,
 * `ossdk.swift_version`, plus any `additionalVersionAttributes`) ride along so
 * dashboards can filter by the app's language stack — mirrors KMP LogFieldsTopLevel.
 */
internal class OtelFieldsTopLevel(
    private val platformProvider: IOtelPlatformProvider,
) {
    suspend fun getAttributes(): Map<String, String> {
        val attributes: MutableMap<String, String> = mutableMapOf()

        // Extras first so dedicated / core fields below always win on clash.
        for ((key, value) in platformProvider.additionalVersionAttributes) {
            val suffix = normalizeOssdkAttributeSuffix(key)
            if (suffix.isNotEmpty() && !value.isNullOrBlank()) {
                attributes["ossdk.$suffix"] = value
            }
        }

        attributes.putAll(
            mapOf(
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
            ),
        )

        attributes
            .putIfValueNotNull("ossdk.sdk_wrapper", platformProvider.sdkWrapper)
            .putIfValueNotNull("ossdk.sdk_wrapper_version", platformProvider.sdkWrapperVersion)
            .putIfValueNotBlank("ossdk.kotlin_version", platformProvider.kotlinVersion)
            .putIfValueNotBlank("ossdk.swift_version", platformProvider.swiftVersion)

        return attributes.toUnmodifiableMap()
    }
}

internal fun <K, V> MutableMap<K, V>.putIfValueNotNull(key: K, value: V?): MutableMap<K, V> {
    if (value != null) {
        this[key] = value
    }
    return this
}

/** Like [putIfValueNotNull], but also skips blank strings so filter attrs stay sparse. */
internal fun MutableMap<String, String>.putIfValueNotBlank(
    key: String,
    value: String?,
): MutableMap<String, String> {
    if (!value.isNullOrBlank()) {
        this[key] = value
    }
    return this
}

/**
 * Hosts may pass bare suffixes (`java_version`) or accidentally include the
 * `ossdk.` prefix (with optional surrounding whitespace); trim first so
 * `" ossdk.foo"` does not become `ossdk.ossdk.foo`.
 */
internal fun normalizeOssdkAttributeSuffix(key: String): String =
    key.trim().removePrefix("ossdk.").trim()
