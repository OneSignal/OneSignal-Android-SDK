package com.onesignal.core.internal.features

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import com.onesignal.features.IStartupFlagsStore
import com.onesignal.features.StartupFlags
import com.onesignal.features.StartupFlagsLatch

/**
 * Process-wide holder for the shared [StartupFlagsLatch], backed by a small prefs file so pre-init
 * code (often on main) never loads the OneSignal store. See README.md in this package.
 */
internal object StartupFlagsCache {
    @Volatile
    private var latch = StartupFlagsLatch()

    /** The first caller in the process reads the file; everyone after gets the same answer. */
    fun snapshot(context: () -> Context?): StartupFlags = latch.snapshot { context()?.let(::PrefsStartupFlagsStore) }

    private val writeLock = Any()

    /** Mirrors the APP_STARTUP subset of [remoteFlags] for the next process. Call off the main thread. */
    fun write(
        context: Context,
        remoteFlags: List<String>,
    ) = write(context) { remoteFlags }

    /** [currentFlags] runs under the write lock, so racing writers always leave the latest flags on disk. */
    fun write(
        context: Context,
        currentFlags: () -> List<String>,
    ) {
        synchronized(writeLock) { latch.mirror(PrefsStartupFlagsStore(context), currentFlags()) }
    }

    /** Reads the file now, without touching this process's snapshot. */
    internal fun readFile(context: Context?): StartupFlags = StartupFlagsLatch().snapshot { context?.let(::PrefsStartupFlagsStore) }

    /** Null forgets the snapshot, as a new process would. */
    internal fun setSnapshotForTest(flags: StartupFlags?) {
        latch =
            StartupFlagsLatch().also { fresh ->
                if (flags != null) fresh.snapshot { FixedStore(flags.keys) }
            }
    }

    private class FixedStore(
        private val keys: Set<String>,
    ) : IStartupFlagsStore {
        override fun read(): Set<String> = keys

        override fun write(keys: Set<String>) = Unit
    }
}

internal class PrefsStartupFlagsStore(
    private val context: Context,
) : IStartupFlagsStore {
    @Suppress("TooGenericExceptionCaught")
    override fun read(): Set<String>? =
        try {
            prefs().getStringSet(KEY_ENABLED, null)?.toSet()
        } catch (t: Throwable) {
            // android.util.Log, not Logging: Logging can dispatch, and this can run before SDK init.
            Log.w(TAG, "startup flags unreadable, treating all as off", t)
            throw t
        }

    // commit, not apply: the next process reads this at startup, so it must be on disk when we return.
    override fun write(keys: Set<String>) {
        check(prefs().edit().putStringSet(KEY_ENABLED, keys).commit()) { "startup flags commit failed" }
    }

    private fun prefs(): SharedPreferences = storageContext(context).getSharedPreferences(STORE, Context.MODE_PRIVATE)

    internal companion object {
        const val STORE = "OneSignalStartupFlags"
        private const val KEY_ENABLED = "enabled"
        private const val TAG = "OneSignal"

        // Device-protected so receivers and jobs that start before first unlock read the real values.
        // Must hold nothing secret: this storage is readable while the device is still locked.
        fun storageContext(context: Context): Context {
            val app = context.applicationContext
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) app.createDeviceProtectedStorageContext() else app
        }
    }
}
