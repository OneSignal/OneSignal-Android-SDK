package com.onesignal.common.threading

import com.onesignal.debug.internal.logging.Logging

/**
 * Global threading mode switch that can be refreshed from remote config.
 */
internal object ThreadingMode {
    @Volatile
    var useBackgroundThreading: Boolean = false

    fun updateUseBackgroundThreading(
        enabled: Boolean,
        source: String,
    ) {
        val previous = useBackgroundThreading
        useBackgroundThreading = enabled

        if (previous != enabled) {
            Logging.info("OneSignal: ThreadingMode changed to useBackgroundThreading=$enabled (source=$source)")
        } else {
            Logging.debug("OneSignal: ThreadingMode unchanged (useBackgroundThreading=$enabled, source=$source)")
        }
    }
}
