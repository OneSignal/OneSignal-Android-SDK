package com.onesignal.internal

import android.content.Context
import com.onesignal.IOneSignal
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.features.FeatureFlag
import org.json.JSONArray

/**
 * Chooses the [IOneSignal] implementation once per process, from the feature flags cached by the
 * previous run, so a remote change applies on the next cold start. Any failure keeps the legacy behavior.
 */
internal object NeverCrashKillSwitch {
    // Absent means the legacy implementation, so the rollout stays off until Turbine sends the flag.
    val FLAG_KEY: String = FeatureFlag.SDK_NEVER_CRASH_ENABLED.key

    // Local testing only: true forces the legacy implementation, false the new one, null reads the flag.
    private val localOverride: Boolean? = null

    @Volatile
    var useLegacy: Boolean = true
        private set

    @Volatile
    var evaluated: Boolean = false
        private set

    private val evaluateLock = Any()

    // First call wins. A later read must not disagree with the implementation already created.
    // Must not throw: process start and the first SDK call both land here.
    @Suppress("TooGenericExceptionCaught")
    fun evaluate(context: Context) {
        if (evaluated) return
        synchronized(evaluateLock) {
            if (evaluated) return
            useLegacy = localOverride ?: try {
                !isEnabledByCachedFlags(context)
            } catch (t: Throwable) {
                Logging.warn("OneSignal: could not read cached feature flags, keeping the legacy init behavior", t)
                true
            }
            evaluated = true
        }
    }

    // Null does not invent a Context. Init passes one, or the startup provider already evaluated.
    fun select(context: Context?): IOneSignal {
        if (context != null) evaluate(context)
        return create()
    }

    fun create(): IOneSignal =
        when {
            !evaluated -> {
                Logging.warn("OneSignal: rollout flag not evaluated before first use, keeping the legacy init behavior")
                OneSignalImp()
            }
            !useLegacy -> {
                Logging.debug("OneSignal: $FLAG_KEY is on, using the never-crash init behavior")
                OneSignalImpV2()
            }
            else -> OneSignalImp()
        }

    internal fun isEnabledByCachedFlags(context: Context): Boolean {
        // Credential-encrypted prefs throw on access before the user unlocks the device.
        val keys = if (AndroidUtils.isAndroidUserUnlocked(context)) cachedFeatureKeys(context) else null
        return keys?.any { it.equals(FLAG_KEY, ignoreCase = true) } == true
    }

    private fun cachedFeatureKeys(context: Context): List<String>? {
        val stored =
            context
                .getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
                .getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + CONFIG_NAME_SPACE, null)
        val keys = stored?.let { JSONArray(it).optJSONObject(0)?.optJSONArray(ConfigModel::sdkRemoteFeatureFlags.name) }
        return keys?.let { array -> (0 until array.length()).map { array.optString(it) } }
    }

    internal fun resetForTest() {
        useLegacy = true
        evaluated = false
    }
}
