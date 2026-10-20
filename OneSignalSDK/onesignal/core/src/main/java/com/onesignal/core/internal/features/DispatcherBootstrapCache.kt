package com.onesignal.core.internal.features

import android.content.Context
import android.util.Log
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.features.FeatureFlag
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cached sdkRemoteFeatureFlags, read directly so a cold receiver can latch
 * before FeatureManager exists. Unreadable cache means off.
 */
internal enum class BootstrapCacheRead { ENABLED, DISABLED, UNREADABLE }

@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal object DispatcherBootstrapCache {
    fun read(context: Context): BootstrapCacheRead =
        try {
            val raw =
                context.applicationContext
                    .getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
                    .getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + CONFIG_NAME_SPACE, null)
            classify(raw)
        } catch (t: Throwable) {
            // android.util.Log, not Logging: Logging can dispatch, and this runs before pools exist.
            Log.w(TAG, "sdk_dispatcher_bootstrap: cache unreadable, lazy pools", t)
            BootstrapCacheRead.UNREADABLE
        }

    private fun classify(raw: String?): BootstrapCacheRead {
        val models = if (raw.isNullOrBlank()) null else parseModels(raw)
        val names = models?.let { flagNames(it) }.orEmpty()
        return when {
            raw.isNullOrBlank() -> BootstrapCacheRead.DISABLED
            models == null -> BootstrapCacheRead.UNREADABLE
            names.any { it.equals(FeatureFlag.SDK_DISPATCHER_BOOTSTRAP.key, ignoreCase = true) } ->
                BootstrapCacheRead.ENABLED
            else -> BootstrapCacheRead.DISABLED
        }
    }

    internal fun containsKey(
        raw: String?,
        key: String,
    ): Boolean = parseModels(raw)?.let { flagNames(it) }.orEmpty().any { it.equals(key, ignoreCase = true) }

    private fun flagNames(models: JSONArray): List<String> =
        buildList {
            for (modelIndex in 0 until models.length()) {
                collectFlags(models.optJSONObject(modelIndex), this)
            }
        }

    private fun parseModels(raw: String?): JSONArray? =
        if (raw.isNullOrBlank()) {
            null
        } else {
            try {
                JSONArray(raw)
            } catch (t: Throwable) {
                // JSONException's message is the whole document. That store can hold fcmParams.apiKey.
                Log.w(TAG, "sdk_dispatcher_bootstrap: config store unreadable, lazy pools (${t.javaClass.simpleName})")
                null
            }
        }

    private fun collectFlags(
        model: JSONObject?,
        into: MutableList<String>,
    ) {
        val flags = model?.optJSONArray(FLAGS_PROPERTY) ?: return
        for (flagIndex in 0 until flags.length()) {
            into.add(flags.optString(flagIndex))
        }
    }

    private const val FLAGS_PROPERTY = "sdkRemoteFeatureFlags"
    private const val TAG = "OneSignal"
}
