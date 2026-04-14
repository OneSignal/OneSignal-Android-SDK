package com.onesignal.common

import com.onesignal.debug.internal.logging.Logging

/**
 * Global feature switch for foldable device IAM display improvements.
 * When enabled, uses modern WindowMetrics API and detects screen size changes.
 */
internal object FoldableIAMFeature {
    @Volatile
    var isEnabled: Boolean = false
        private set

    fun updateEnabled(
        enabled: Boolean,
        source: String,
    ) {
        val previous = isEnabled
        isEnabled = enabled

        if (previous != enabled) {
            Logging.info("OneSignal: FoldableIAMFeature changed to isEnabled=$enabled (source=$source)")
        } else {
            Logging.debug("OneSignal: FoldableIAMFeature unchanged (isEnabled=$enabled, source=$source)")
        }
    }
}
