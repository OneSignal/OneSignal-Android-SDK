package com.onesignal.internal

import android.content.Context
import com.onesignal.IOneSignal
import com.onesignal.common.AndroidUtils
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.Logging
import org.json.JSONArray

/**
 * Chooses the [IOneSignal] implementation once per process, from the feature flags cached by the
 * previous run, so a remote change applies on the next cold start. Any failure keeps the new behavior.
 */
internal object NeverCrashKillSwitch {
    // Present means off, so installs whose init never succeeds (and never fetch flags) keep the safe default.
    const val FLAG_KEY = "sdk_never_crash_disabled"

    // Local testing only: true forces the legacy implementation, false the new one, null reads the flag.
    private val localOverride: Boolean? = null

    @Volatile
    var useLegacy: Boolean = false
        private set

    @Volatile
    var evaluated: Boolean = false
        private set

    // Runs on the main thread before Application.onCreate, so it must never throw.
    @Suppress("TooGenericExceptionCaught")
    fun evaluate(context: Context) {
        useLegacy = localOverride ?: try {
            isDisabledByCachedFlags(context)
        } catch (t: Throwable) {
            Logging.warn("OneSignal: could not read cached feature flags, keeping the default init behavior", t)
            false
        }
        evaluated = true
    }

    fun create(): IOneSignal =
        when {
            !evaluated -> {
                Logging.debug("OneSignal: kill switch not evaluated before first use, keeping the default init behavior")
                OneSignalImp()
            }
            useLegacy -> {
                Logging.warn("OneSignal: $FLAG_KEY is on, using the legacy init behavior")
                LegacyOneSignalImp()
            }
            else -> OneSignalImp()
        }

    internal fun isDisabledByCachedFlags(context: Context): Boolean {
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
        useLegacy = false
        evaluated = false
    }
}
