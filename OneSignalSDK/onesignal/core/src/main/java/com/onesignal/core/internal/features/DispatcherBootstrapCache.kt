package com.onesignal.core.internal.features

import android.content.Context
import com.onesignal.core.internal.config.CONFIG_NAME_SPACE
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.features.FeatureFlag
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cached sdkRemoteFeatureFlags, read directly so a cold receiver can latch
 * before FeatureManager exists. Unreadable cache means off.
 */
@Suppress("TooGenericExceptionCaught", "SwallowedException")
internal object DispatcherBootstrapCache {
    fun isEnabled(context: Context): Boolean =
        try {
            val raw =
                context.applicationContext
                    .getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
                    .getString(PreferenceOneSignalKeys.MODEL_STORE_PREFIX + CONFIG_NAME_SPACE, null)
            containsKey(raw, FeatureFlag.SDK_DISPATCHER_BOOTSTRAP.key)
        } catch (t: Throwable) {
            Logging.warn(
                "DispatcherBootstrapCache: unreadable, treating bootstrap as off: ${t.message}",
                t,
            )
            false
        }

    internal fun containsKey(
        raw: String?,
        key: String,
    ): Boolean = flagNames(raw).any { it.equals(key, ignoreCase = true) }

    private fun flagNames(raw: String?): List<String> =
        parseModels(raw)?.let { models ->
            buildList {
                for (modelIndex in 0 until models.length()) {
                    collectFlags(models.optJSONObject(modelIndex), this)
                }
            }
        } ?: emptyList()

    private fun parseModels(raw: String?): JSONArray? =
        if (raw.isNullOrBlank()) {
            null
        } else {
            try {
                JSONArray(raw)
            } catch (_: Throwable) {
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
}
