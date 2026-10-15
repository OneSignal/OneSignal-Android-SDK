package com.onesignal.user.internal

import com.onesignal.common.hasNullByte
import com.onesignal.common.isMissing
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.internal.SdkOperation

internal fun resolveAppIdV2(
    inputAppId: String?,
    configModel: ConfigModel,
    preferencesService: IPreferencesService,
): AppIdResolution {
    val validInputAppId = inputAppId?.takeUnless { isMissing(it, SdkOperation.INIT_WITH_CONTEXT, "appId") }

    // Case 1: AppId provided as input
    if (validInputAppId != null) {
        val forceCreateUser = !configModel.hasProperty(ConfigModel::appId.name) || configModel.appId != validInputAppId
        return AppIdResolution(appId = validInputAppId, forceCreateUser = forceCreateUser, failed = false)
    }

    // Case 2: No appId provided, but configModel has one.
    // Cached values can be invalid on upgraded installs, so they are validated too.
    if (configModel.hasProperty(ConfigModel::appId.name) && isUsableAppId(configModel.appId)) {
        return AppIdResolution(appId = configModel.appId, forceCreateUser = false, failed = false)
    }

    // Case 3: No appId provided, no configModel appId - try legacy
    val legacyAppId = preferencesService.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID)
    if (isUsableAppId(legacyAppId)) {
        return AppIdResolution(appId = legacyAppId, forceCreateUser = true, failed = false)
    }

    return AppIdResolution(appId = null, forceCreateUser = false, failed = true)
}

private fun isUsableAppId(appId: String?): Boolean = !appId.isNullOrEmpty() && !hasNullByte(appId)
