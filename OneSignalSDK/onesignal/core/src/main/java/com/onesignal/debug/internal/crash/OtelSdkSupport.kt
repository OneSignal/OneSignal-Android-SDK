package com.onesignal.debug.internal.crash

import android.os.Build

/**
 * Centralizes the SDK version requirement for Otel-based features
 * (crash reporting, ANR detection, remote log shipping).
 *
 * [isSupported] is writable internally so that unit tests can override
 * the device-level gate without Robolectric @Config gymnastics.
 */
internal object OtelSdkSupport {
    /** Otel libraries require Android O (API 26) or above. */
    const val MIN_SDK_VERSION = Build.VERSION_CODES.O // 26

    /**
     * Whether the current device meets the minimum SDK requirement.
     * Production code should treat this as read-only; tests may flip it via [reset]/direct set.
     */
    var isSupported: Boolean = Build.VERSION.SDK_INT >= MIN_SDK_VERSION
        internal set

    /** Restores the runtime-detected value — call in test teardown. */
    fun reset() {
        isSupported = Build.VERSION.SDK_INT >= MIN_SDK_VERSION
    }
}
