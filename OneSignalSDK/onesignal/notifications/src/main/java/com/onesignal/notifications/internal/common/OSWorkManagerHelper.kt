package com.onesignal.notifications.internal.common

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.onesignal.debug.internal.logging.Logging

object OSWorkManagerHelper {
    /**
     * If there is an instance of WorkManager available, use it. Else, in rare cases, initialize it ourselves.
     *
     * Calling `WorkManager.getInstance(context)` directly can cause an exception if it is null.
     * However, this is the appropriate way to check as the non-throwing `WorkManager.getInstance()`
     * method does not allow the opportunity for on-demand initialization with custom WorkManager.
     *
     * The purpose of this helper is to work around this bug - https://issuetracker.google.com/issues/258176803
     *
     * @return an instance of WorkManager
     */
    @Synchronized
    fun getInstance(context: Context): WorkManager {
        return try {
            WorkManager.getInstance(context)
        } catch (e: IllegalStateException) {
            /*
            This aims to catch the IllegalStateException "WorkManager is not initialized properly..." -
            https://androidx.tech/artifacts/work/work-runtime/2.8.1-source/androidx/work/impl/WorkManagerImpl.java.html
             */
            Logging.warn("OSWorkManagerHelper.getInstance failed, attempting to initialize: ", e)
            initializeWorkManager(context)
            WorkManager.getInstance(context)
        }
    }

    /**
     * This method is called in rare cases to initialize WorkManager ourselves.
     */
    private fun initializeWorkManager(context: Context) {
        try {
            /*
            Note: `WorkManager.getInstance(context)` should already initialize WorkManager when the
            application implements Configuration.Provider. However, as a further safeguard, let's detect
            for this when we attempt to initialize WorkManager.
             */
            val configuration = (context.applicationContext as? Configuration.Provider)?.workManagerConfiguration ?: Configuration.Builder().build()
            WorkManager.initialize(context, configuration)
        } catch (e: IllegalStateException) {
            /*
            This catch is meant for the exception -
            https://android.googlesource.com/platform/frameworks/support/+/60ae0eec2a32396c22ad92502cde952c80d514a0/work/workmanager/src/main/java/androidx/work/impl/WorkManagerImpl.java#177
            1. We lost the race with another call to  WorkManager.initialize outside of OneSignal.
            2. It is possible for some other unexpected error is thrown from WorkManager.
             */
            Logging.warn("OSWorkManagerHelper initializing WorkManager failed: ", e)
        }
    }
}
