package com.onesignal.debug.internal.logging.otel.attributes

import android.os.Build
import com.onesignal.common.AndroidUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.OneSignalWrapper
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.device.IInstallIdService
import com.squareup.wire.internal.toUnmodifiableMap

// Used on all attributes / fields we add to Otel events that is NOT part of
// their spec. We do this to make it clear where the source of this field is.
internal const val OS_OTEL_NAMESPACE: String = "ossdk"

/**
 * Purpose: Fields to be included in every Otel request that goes out.
 * Requirements: Only include fields that can NOT change during runtime,
 * as these are only fetched once. (Calculated fields are ok)
 */
internal class OneSignalOtelFieldsTopLevel(
    private val _applicationService: IApplicationService,
    private val _installIdService: IInstallIdService,
) {
    suspend fun getAttributes(): Map<String, String> {
        val attributes: MutableMap<String, String> =
            mutableMapOf(
                "$OS_OTEL_NAMESPACE.install_id" to
                    _installIdService.getId().toString(),
                "$OS_OTEL_NAMESPACE.sdk_base"
                    to "android",
                "$OS_OTEL_NAMESPACE.sdk_base_version" to
                    OneSignalUtils.sdkVersion,
                "$OS_OTEL_NAMESPACE.app_package_id" to
                    _applicationService.appContext.packageName,
                "$OS_OTEL_NAMESPACE.app_version" to
                    (AndroidUtils.getAppVersion(_applicationService.appContext) ?: "unknown"),
                "device.manufacturer"
                    to Build.MANUFACTURER,
                "device.model.identifier"
                    to Build.MODEL,
                "os.name"
                    to "Android",
                "os.version"
                    to Build.VERSION.RELEASE,
                "os.build_id"
                    to Build.ID,
            )

        attributes
            .putIfValueNotNull(
                "$OS_OTEL_NAMESPACE.sdk_wrapper",
                OneSignalWrapper.sdkType
            ).putIfValueNotNull(
                "$OS_OTEL_NAMESPACE.sdk_wrapper_version",
                OneSignalWrapper.sdkVersion
            )

        return attributes.toUnmodifiableMap()
    }
}

internal fun <K, V> MutableMap<K, V>.putIfValueNotNull(key: K, value: V?): MutableMap<K, V> {
    if (value != null) {
        this[key] = value
    }
    return this
}
