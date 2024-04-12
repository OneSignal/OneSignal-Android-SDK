package com.onesignal.notifications.internal.common

import android.annotation.SuppressLint
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.impl.WorkManagerImpl
import com.onesignal.debug.internal.logging.Logging

object OSWorkManagerHelper {
    /**
     * Helper method to provide a way to check if WorkManager is initialized in this process.
     * The purpose of this helper is to work around this bug - https://issuetracker.google.com/issues/258176803
     *
     * This is effectively the `WorkManager.isInitialized()` public method introduced in androidx.work:work-*:2.8.0-alpha02.
     * Please see https://android-review.googlesource.com/c/platform/frameworks/support/+/1941186.
     *
     * @return `true` if WorkManager has been initialized in this process.
     */
    @SuppressWarnings("deprecation")
    @SuppressLint("RestrictedApi")
    private fun isInitialized(): Boolean {
        return WorkManagerImpl.getInstance() != null
    }

    /**
     * If there is an instance of WorkManager available, use it. Else, in rare cases, initialize it ourselves.
     *
     * Calling `WorkManager.getInstance(context)` directly can cause an exception if it is null.
     *
     * @return an instance of WorkManager
     */
    @JvmStatic
    @Synchronized
    fun getInstance(context: Context): WorkManager {
        if (!isInitialized()) {
            try {
                WorkManager.initialize(context, Configuration.Builder().build())
            } catch (e: IllegalStateException) {
                /*
                This catch is meant for the exception -
                https://android.googlesource.com/platform/frameworks/support/+/60ae0eec2a32396c22ad92502cde952c80d514a0/work/workmanager/src/main/java/androidx/work/impl/WorkManagerImpl.java#177
                1. We lost the race with another call to  WorkManager.initialize outside of OneSignal.
                2. It is possible for some other unexpected error is thrown from WorkManager.
                 */
                Logging.error("OSWorkManagerHelper initializing WorkManager failed: $e")
            }
        }
        return WorkManager.getInstance(context)
    }
}
