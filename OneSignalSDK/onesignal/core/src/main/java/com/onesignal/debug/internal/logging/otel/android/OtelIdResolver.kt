package com.onesignal.debug.internal.logging.otel.android

import android.content.Context
import com.onesignal.common.IDManager
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.user.internal.backend.IdentityConstants
import org.json.JSONArray
import org.json.JSONObject

/**
 * Resolves OneSignal IDs from SharedPreferences with fallback strategies.
 * This class encapsulates all the logic for reading IDs from ConfigModelStore and legacy SharedPreferences,
 * making it easier to maintain and test.
 *
 * Note: Data is read fresh from SharedPreferences each time (not cached) to ensure test reliability
 * and correctness. The performance impact is minimal since these methods are not called frequently.
 */
@Suppress("TooManyFunctions") // This class intentionally groups related ID resolution functions
internal class OtelIdResolver(
    private val context: Context?,
) {
    companion object {
        /**
         * Default error appId prefix when appId cannot be resolved.
         */
        private const val ERROR_APP_ID_PREFIX = "8123-1231-4343-2323-error-"
    }

    // Get SharedPreferences instance (fresh each time to avoid caching issues in tests)
    private fun getSharedPreferences(): android.content.SharedPreferences? {
        return context?.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
    }

    // Read ConfigModelStore JSON (fresh read each time for testability)
    // In production, this is called multiple times per resolver instance, but the performance impact is minimal
    // and this ensures test reliability
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun readConfigModel(): JSONObject? {
        return try {
            val configStoreJson = getSharedPreferences()?.getString(
                PreferenceOneSignalKeys.MODEL_STORE_PREFIX + com.onesignal.core.internal.config.CONFIG_NAME_SPACE,
                null
            )

            if (configStoreJson != null && configStoreJson.isNotEmpty()) {
                val jsonArray = JSONArray(configStoreJson)
                if (jsonArray.length() > 0) {
                    jsonArray.getJSONObject(0)
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // Check if ConfigModelStore exists but is empty (to distinguish from "not found")
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun hasEmptyConfigStore(): Boolean {
        return try {
            val configStoreJson = getSharedPreferences()?.getString(
                PreferenceOneSignalKeys.MODEL_STORE_PREFIX + com.onesignal.core.internal.config.CONFIG_NAME_SPACE,
                null
            )
            if (configStoreJson != null && configStoreJson.isNotEmpty()) {
                val jsonArray = JSONArray(configStoreJson)
                jsonArray.length() == 0
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolves appId with the following fallback chain:
     * 1. Try ConfigModelStore in SharedPreferences (MODEL_STORE_config)
     * 2. Try legacy OneSignal SharedPreferences
     * 3. Return error appId with affix if all fail
     */
    @Suppress("TooGenericExceptionCaught")
    fun resolveAppId(): String {
        return try {
            val configModel = readConfigModel()
            val appIdFromConfig = extractAppIdFromConfig(configModel)
            appIdFromConfig ?: resolveAppIdFromLegacy(configModel)
        } catch (e: Exception) {
            "$ERROR_APP_ID_PREFIX${e.javaClass.simpleName}"
        }
    }

    private fun extractAppIdFromConfig(configModel: JSONObject?): String? {
        if (configModel == null) return null
        val appIdProperty = ConfigModel::appId
        return if (configModel.has(appIdProperty.name)) {
            val appId = configModel.getString(appIdProperty.name)
            appId.ifEmpty { null }
        } else {
            null
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth")
    private fun resolveAppIdFromLegacy(configModel: JSONObject?): String {
        // Second: fall back to legacy OneSignal SharedPreferences
        val legacyAppId = try {
            getSharedPreferences()?.getString(PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID, null)
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }

        return legacyAppId ?: run {
            // Third: return error appId with affix
            val errorAffix = when {
                context == null -> "no-context"
                hasEmptyConfigStore() -> "no-appid-in-config" // Store exists but is empty array
                configModel == null -> "config-store-not-found" // Store doesn't exist
                !configModel.has("appId") -> "no-appid-in-config" // Store exists but no appId field
                else -> "unknown"
            }
            "$ERROR_APP_ID_PREFIX$errorAffix"
        }
    }

    /**
     * Resolves onesignalId with the following fallback chain:
     * 1. Try IdentityModelStore in SharedPreferences (MODEL_STORE_identity)
     * 2. Return null if all fail
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth")
    fun resolveOnesignalId(): String? {
        return try {
            val identityStoreJson = getSharedPreferences()?.getString(
                PreferenceOneSignalKeys.MODEL_STORE_PREFIX + com.onesignal.user.internal.identity.IDENTITY_NAME_SPACE,
                null
            )

            if (identityStoreJson != null && identityStoreJson.isNotEmpty()) {
                extractOnesignalIdFromJson(identityStoreJson)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractOnesignalIdFromJson(identityStoreJson: String): String? {
        val jsonArray = JSONArray(identityStoreJson)
        if (jsonArray.length() > 0) {
            val identityModel = jsonArray.getJSONObject(0)
            if (identityModel.has(IdentityConstants.ONESIGNAL_ID)) {
                val onesignalId = identityModel.getString(IdentityConstants.ONESIGNAL_ID)
                return onesignalId.takeIf { it.isNotEmpty() && !IDManager.isLocalId(it) }
            }
        }
        return null
    }

    /**
     * Resolves pushSubscriptionId from cached ConfigModelStore.
     * Returns null if not found or if it's a local ID.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun resolvePushSubscriptionId(): String? {
        return try {
            val configModel = readConfigModel()
            val pushSubscriptionIdProperty = ConfigModel::pushSubscriptionId
            if (configModel != null && configModel.has(pushSubscriptionIdProperty.name)) {
                val pushSubscriptionId = configModel.getString(pushSubscriptionIdProperty.name)
                pushSubscriptionId.takeIf { it.isNotEmpty() && !IDManager.isLocalId(pushSubscriptionId) }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolves remote log level from cached ConfigModelStore.
     * Returns null if not found or if there's an error.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException", "NestedBlockDepth")
    fun resolveRemoteLogLevel(): com.onesignal.debug.LogLevel? {
        return try {
            val configModel = readConfigModel()
            val remoteLoggingParamsProperty = ConfigModel::remoteLoggingParams
            if (configModel != null && configModel.has(remoteLoggingParamsProperty.name)) {
                extractLogLevelFromParams(configModel.getJSONObject(remoteLoggingParamsProperty.name))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun extractLogLevelFromParams(remoteLoggingParams: JSONObject): com.onesignal.debug.LogLevel? {
        return if (remoteLoggingParams.has("logLevel")) {
            val logLevelString = remoteLoggingParams.getString("logLevel")
            try {
                com.onesignal.debug.LogLevel.valueOf(logLevelString.uppercase())
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Resolves install ID from SharedPreferences.
     * Returns "InstallId-Null" if not found, "InstallId-NotFound" if there's an error.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun resolveInstallId(): String {
        return try {
            val installIdString = getSharedPreferences()?.getString(PreferenceOneSignalKeys.PREFS_OS_INSTALL_ID, "InstallId-Null")
            installIdString ?: "InstallId-Null"
        } catch (e: Exception) {
            "InstallId-NotFound"
        }
    }
}
