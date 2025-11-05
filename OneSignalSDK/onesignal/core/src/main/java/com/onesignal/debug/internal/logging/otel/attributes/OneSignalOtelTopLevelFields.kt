package com.onesignal.debug.internal.logging.otel.attributes

import android.os.Build
import com.onesignal.common.AndroidUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.OneSignalWrapper
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IInstallIdService
import com.squareup.wire.internal.toUnmodifiableMap

/**
 * Purpose: Fields to be included in every Otel request that goes out.
 * Requirements: Only include fields that can NOT change during runtime,
 * as these are only fetched once. (Calculated fields are ok)
 */
class OneSignalOtelTopLevelFields(
    private val _applicationService: IApplicationService,
    private val _configModelStore: ConfigModelStore,
    private val _installIdService: IInstallIdService,
) {
    suspend fun getAttributes(): Map<String, String> {
        val attributes:  MutableMap<String, String> =
            mutableMapOf(
                "ossdk.app_id" to _configModelStore.model.appId,
                "ossdk.install_id" to _installIdService.getId().toString(),
                "ossdk.sdk_base" to "android",
                "ossdk.sdk_base_version" to OneSignalUtils.sdkVersion,
                "ossdk.app_package_id" to
                    _applicationService.appContext.packageName,
                "ossdk.app_version" to
                    (AndroidUtils.getAppVersion(_applicationService.appContext) ?: "unknown"),
                "device.manufacturer" to Build.MANUFACTURER,
                "device.model.identifier" to Build.MODEL,
                "os.name" to "Android",
                "os.version" to Build.VERSION.RELEASE,
                "os.build_id" to Build.ID,
            )

        attributes
            .putIfValueNotNull(
                "ossdk.sdk_wrapper",
                OneSignalWrapper.sdkType
            )
            .putIfValueNotNull(
                "ossdk.sdk_wrapper_version",
                OneSignalWrapper.sdkVersion
            )

        return attributes.toUnmodifiableMap()
    }
}

internal fun <K, V> MutableMap<K, V>.putIfValueNotNull(key: K, value: V?): MutableMap<K, V> {
    if (value != null)
        this[key] = value
    return this
}
