package com.onesignal.debug.internal.crash

import android.os.Build

/** The single SDK-version gate for observability: crash reporting, ANR detection, log shipping. */
internal object ObservabilitySdkSupport {
    /** Matches the host SDK minSdk. Tests flip [isSupported] without Robolectric. */
    const val MIN_SDK_VERSION = Build.VERSION_CODES.LOLLIPOP // 21

    /** Read-only in production; writable only so tests can flip the gate without Robolectric. */
    var isSupported: Boolean = Build.VERSION.SDK_INT >= MIN_SDK_VERSION
        internal set

    /** Restores the runtime-detected value — call in test teardown. */
    fun reset() {
        isSupported = Build.VERSION.SDK_INT >= MIN_SDK_VERSION
    }
}
