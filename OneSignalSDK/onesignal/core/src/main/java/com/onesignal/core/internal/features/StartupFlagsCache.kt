package com.onesignal.core.internal.features

import android.content.Context
import android.util.Log
import com.onesignal.features.FeatureActivationMode
import com.onesignal.features.FeatureFlag

/** APP_STARTUP flags as one process sees them. */
internal class StartupFlags(
    val source: Source,
    private val enabledKeys: Set<String>,
) {
    enum class Source { FILE, UNREADABLE, NO_APPLICATION }

    val keys: Set<String> get() = enabledKeys

    fun isEnabled(flag: FeatureFlag): Boolean {
        require(flag.activationMode == FeatureActivationMode.APP_STARTUP) { "${flag.key} is not APP_STARTUP" }
        return flag.key in enabledKeys
    }
}

/**
 * Small prefs file holding the cached APP_STARTUP flags, read once per process so pre-store
 * code (often on main) and [FeatureManager] latch the same values. See README.md in this package.
 */
@Suppress("TooGenericExceptionCaught")
internal object StartupFlagsCache {
    @Volatile
    private var snapshot: StartupFlags? = null
    private val lock = Any()

    /** The first caller in the process reads the file; everyone after gets the same answer. */
    fun snapshot(context: () -> Context?): StartupFlags {
        snapshot?.let { return it }
        synchronized(lock) {
            snapshot?.let { return it }
            return readFile(context()).also { snapshot = it }
        }
    }

    internal fun readFile(context: Context?): StartupFlags {
        if (context == null) return StartupFlags(StartupFlags.Source.NO_APPLICATION, emptySet())
        return try {
            StartupFlags(StartupFlags.Source.FILE, prefs(context).getStringSet(KEY_ENABLED, null).orEmpty().toSet())
        } catch (t: Throwable) {
            // android.util.Log, not Logging: Logging can dispatch, and this can run before SDK init.
            Log.w(TAG, "startup flags unreadable, treating all as off", t)
            StartupFlags(StartupFlags.Source.UNREADABLE, emptySet())
        }
    }

    /** Mirrors the APP_STARTUP subset of [remoteFlags] for the next process. Call off the main thread. */
    fun write(
        context: Context,
        remoteFlags: List<String>,
    ) {
        val enabled =
            FeatureFlag.entries
                .filter { flag ->
                    flag.activationMode == FeatureActivationMode.APP_STARTUP &&
                        remoteFlags.any { it.equals(flag.key, ignoreCase = true) }
                }.map { it.key }
                .toSet()
        val prefs = prefs(context)
        if (prefs.getStringSet(KEY_ENABLED, emptySet()) == enabled) return
        prefs.edit().putStringSet(KEY_ENABLED, enabled).apply()
    }

    /** Null forgets the snapshot, as a new process would. */
    internal fun setSnapshotForTest(flags: StartupFlags?) {
        synchronized(lock) { snapshot = flags }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private const val STORE = "OneSignalStartupFlags"
    private const val KEY_ENABLED = "enabled"
    private const val TAG = "OneSignal"
}
