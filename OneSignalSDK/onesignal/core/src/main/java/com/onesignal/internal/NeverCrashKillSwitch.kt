package com.onesignal.internal

import android.content.Context
import com.onesignal.IOneSignal
import com.onesignal.core.internal.features.FeatureManager
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.features.FeatureFlag

/**
 * Chooses the [IOneSignal] implementation once per process, from the startup flags the previous run
 * mirrored, so a remote change applies on the next cold start. Any failure keeps the legacy behavior.
 */
internal object NeverCrashKillSwitch {
    // Absent means the legacy implementation, so the rollout stays off until Turbine sends the flag.
    val FLAG_KEY: String = FeatureFlag.SDK_NEVER_CRASH_ENABLED.key

    @Volatile
    var useLegacy: Boolean = true
        private set

    @Volatile
    var evaluated: Boolean = false
        private set

    @Volatile
    private var pendingChoiceLog: Pair<LogLevel, String>? = null

    private val evaluateLock = Any()

    // First call wins. A later read must not disagree with the implementation already created.
    // Must not throw: process start and the first SDK call both land here.
    @Suppress("TooGenericExceptionCaught")
    fun evaluate(context: Context) {
        if (evaluated) return
        synchronized(evaluateLock) {
            if (evaluated) return
            useLegacy = try {
                // The same latched snapshot the dispatcher pool choice reads: a small prefs file in
                // device-protected storage, so this does not load the OneSignal store on main.
                !FeatureManager.startupFlags { context }.isEnabled(FeatureFlag.SDK_NEVER_CRASH_ENABLED)
            } catch (t: Throwable) {
                Logging.warn("OneSignal: could not read the startup flags, keeping the legacy init behavior", t)
                true
            }
            evaluated = true
        }
    }

    // Silent: a log listener firing here would re-enter the caller before the choice is published.
    fun create(): IOneSignal =
        when {
            !evaluated -> {
                pendingChoiceLog =
                    LogLevel.WARN to "OneSignal: rollout flag not evaluated before first use, keeping the legacy init behavior"
                OneSignalImp()
            }
            !useLegacy -> {
                pendingChoiceLog = LogLevel.DEBUG to "OneSignal: $FLAG_KEY is on, using the never-crash init behavior"
                OneSignalImpV2()
            }
            else -> OneSignalImp()
        }

    // Severity is captured at the decision, not here: evaluate() on another thread could flip useLegacy
    // first and drop the "pinned to legacy" line to DEBUG. Always called, so a discarded choice clears.
    fun logChoice(publish: Boolean) {
        val (level, message) = pendingChoiceLog ?: return
        pendingChoiceLog = null
        if (!publish) return
        if (level == LogLevel.WARN) Logging.warn(message) else Logging.debug(message)
    }

    internal fun resetForTest() {
        useLegacy = true
        evaluated = false
        pendingChoiceLog = null
    }
}
