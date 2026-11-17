package com.onesignal.notifications.internal.common

import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import kotlinx.coroutines.CancellationException

object OSWorkManagerHelper {
    /** Stable token so this helper's recovery path can be counted in remote telemetry. */
    private const val TELEMETRY_TOKEN = "sdk_workmanager_init"

    /**
     * Works around https://issuetracker.google.com/issues/258176803 by initializing WorkManager
     * ourselves. Keep the Context overload: only it lets a Configuration.Provider host do that.
     */
    @Synchronized
    fun getInstance(context: Context): WorkManager {
        return try {
            WorkManager.getInstance(context)
        } catch (e: IllegalStateException) {
            // "not initialized properly" from a host with no Configuration.Provider, or direct boot refusing.
            report(LogLevel.WARN, "OSWorkManagerHelper.getInstance failed, attempting to initialize", e)
            val initFailure = initializeWorkManager(context)
            try {
                WorkManager.getInstance(context)
            } catch (retry: IllegalStateException) {
                // Apps that ship only ERROR records never send the warns above, so carry the causes here.
                val failure = WorkManagerUnavailableException(retry, initFailure?.message)
                failure.suppress(e.takeIf { it !== retry })
                failure.suppress(initFailure)
                report(LogLevel.ERROR, "OSWorkManagerHelper recovery failed", failure)
                throw failure
            }
        }
    }

    /**
     * @return the failure that left WorkManager uninitialized, or null once it is initialized.
     */
    private fun initializeWorkManager(context: Context): IllegalStateException? {
        return try {
            // A non-Provider host has no config to reuse; a Provider host would lose its worker factory.
            val configuration =
                (context.applicationContext as? Configuration.Provider)?.workManagerConfiguration
                    ?: Configuration.Builder().build()
            WorkManager.initialize(context, configuration)
            null
        } catch (e: IllegalStateException) {
            report(LogLevel.WARN, "OSWorkManagerHelper initializing WorkManager failed", e)
            e
        }
    }

    private fun Throwable.suppress(cause: Throwable?) {
        // addSuppressed rejects a throwable suppressing itself.
        if (cause != null && cause !== this) addSuppressed(cause)
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private fun report(
        level: LogLevel,
        message: String,
        throwable: Throwable,
    ) {
        try {
            Logging.log(level, "$TELEMETRY_TOKEN: $message", throwable)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A throwing log listener must not replace the WorkManager failure callers handle.
        } catch (e: LinkageError) {
            // Same for a listener compiled against a different SDK.
        }
    }
}

/**
 * Subclasses IllegalStateException so existing callers still catch it. Grouping keys on this
 * message, so it names OneSignal and leads with [reason], the one text that tells causes apart.
 */
internal class WorkManagerUnavailableException(
    cause: IllegalStateException,
    reason: String?,
) : IllegalStateException("OneSignal could not obtain WorkManager: ${reason ?: cause.message}", cause)
