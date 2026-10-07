package com.onesignal.debug.internal.logging.logger.android

import android.content.Context
import com.onesignal.common.IDManager
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.IdentityConstants
import org.json.JSONArray
import org.json.JSONObject

/**
 * Resolves OneSignal IDs from ConfigModelStore and legacy SharedPreferences. Reads are never
 * cached: they are infrequent, and a cache would leak state between tests.
 */
@Suppress("TooManyFunctions")
internal class LoggerIdResolver(
    private val context: Context?,
) {
    companion object {
        /** Returned when appId resolution throws, as opposed to merely finding nothing. */
        private const val ERROR_APP_ID_RESOLVE = "00000000-0000-4000-a000-000000000000"
        private const val ERROR_APP_ID_PREFIX_UNKNOWN = "e1100000-0000-4000-a000-000000000000"
        private const val ERROR_APP_ID_PREFIX_NO_APPID_IN_CONFIG = "e1100000-0000-4000-a000-000000000001"
        private const val ERROR_APP_ID_PREFIX_NO_CONFIG_STORE = "e1100000-0000-4000-a000-000000000002"
        private const val ERROR_APP_ID_PREFIX_NO_APPID_IN_CONFIG_STORE = "e1100000-0000-4000-a000-000000000003"
        private const val ERROR_APP_ID_PREFIX_NO_CONTEXT = "e1100000-0000-4000-a000-000000000004"
    }

    private fun getSharedPreferences(): android.content.SharedPreferences? {
        return context?.getSharedPreferences(PreferenceStores.ONESIGNAL, Context.MODE_PRIVATE)
    }

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
     * Falls back ConfigModelStore, then legacy SharedPreferences, then an error appId whose
     * suffix encodes which lookup failed.
     */
    @Suppress("TooGenericExceptionCaught")
    fun resolveAppId(): String {
        return try {
            val configModel = readConfigModel()
            val appIdFromConfig = extractAppIdFromConfig(configModel)
            appIdFromConfig ?: resolveAppIdFromLegacy(configModel)
        } catch (e: Exception) {
            Logging.error("Trying resolve the app Id${e.message}")
            ERROR_APP_ID_RESOLVE
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
        val legacyAppId = try {
            getSharedPreferences()?.getString(PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID, null)
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }

        return legacyAppId ?: run {
            return when {
                context == null -> ERROR_APP_ID_PREFIX_NO_CONTEXT
                hasEmptyConfigStore() -> ERROR_APP_ID_PREFIX_NO_APPID_IN_CONFIG_STORE
                configModel == null -> ERROR_APP_ID_PREFIX_NO_CONFIG_STORE
                !configModel.has("appId") -> ERROR_APP_ID_PREFIX_NO_APPID_IN_CONFIG
                else -> ERROR_APP_ID_PREFIX_UNKNOWN
            }
        }
    }

    /** Resolves onesignalId from the cached IdentityModelStore; null when absent or still a local ID. */
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

    /** Resolves pushSubscriptionId from the cached ConfigModelStore; null when absent or a local ID. */
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
     * Requires both a usable logLevel and an isEnabled that does not veto it: a server disable
     * rewrites only isEnabled, so trusting the level alone would ignore the kill switch.
     */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun resolveRemoteLoggingEnabled(): Boolean {
        return try {
            val remoteLoggingParams = readRemoteLoggingParams() ?: return false
            val logLevel = extractLogLevelFromParams(remoteLoggingParams)
            isEnabledInParams(remoteLoggingParams) &&
                logLevel != null &&
                logLevel != com.onesignal.debug.LogLevel.NONE
        } catch (e: Exception) {
            false
        }
    }

    /** Resolves the remote log level from the cached ConfigModelStore; null when absent. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun resolveRemoteLogLevel(): com.onesignal.debug.LogLevel? {
        return try {
            readRemoteLoggingParams()?.let { extractLogLevelFromParams(it) }
        } catch (e: Exception) {
            null
        }
    }

    private fun readRemoteLoggingParams(): JSONObject? {
        val configModel = readConfigModel() ?: return null
        val remoteLoggingParamsProperty = ConfigModel::remoteLoggingParams
        return if (configModel.has(remoteLoggingParamsProperty.name)) {
            configModel.getJSONObject(remoteLoggingParamsProperty.name)
        } else {
            null
        }
    }

    /**
     * An absent field means a cache written before isEnabled existed, so it must read as true.
     * A genuine disable always writes the field; reading absent as off would silence upgrades.
     */
    private fun isEnabledInParams(remoteLoggingParams: JSONObject): Boolean =
        !remoteLoggingParams.has("isEnabled") || remoteLoggingParams.optBoolean("isEnabled", true)

    private fun extractLogLevelFromParams(remoteLoggingParams: JSONObject): com.onesignal.debug.LogLevel? =
        com.onesignal.debug.LogLevel.fromString(
            if (remoteLoggingParams.has("logLevel")) remoteLoggingParams.getString("logLevel") else null
        )

    /** Resolves the install ID; "InstallId-Null" when absent, "InstallId-NotFound" on error. */
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
