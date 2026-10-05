package com.onesignal.user.internal

import com.onesignal.common.isMissing
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores

data class AppIdResolution(
    val appId: String?,
    val forceCreateUser: Boolean,
    val failed: Boolean,
)

fun resolveAppId(
    inputAppId: String?,
    configModel: ConfigModel,
    preferencesService: IPreferencesService,
): AppIdResolution {
    val validInputAppId = inputAppId?.takeUnless { isMissing(it, "initialize: appId") }

    // Case 1: AppId provided as input
    if (validInputAppId != null) {
        val forceCreateUser = !configModel.hasProperty(ConfigModel::appId.name) || configModel.appId != validInputAppId
        return AppIdResolution(appId = validInputAppId, forceCreateUser = forceCreateUser, failed = false)
    }

    // Case 2: No appId provided, but configModel has one
    if (configModel.hasProperty(ConfigModel::appId.name)) {
        return AppIdResolution(appId = configModel.appId, forceCreateUser = false, failed = false)
    }

    // Case 3: No appId provided, no configModel appId - try legacy
    val legacyAppId = preferencesService.getString(PreferenceStores.ONESIGNAL, PreferenceOneSignalKeys.PREFS_LEGACY_APP_ID)
    if (legacyAppId != null) {
        return AppIdResolution(appId = legacyAppId, forceCreateUser = true, failed = false)
    }

    // An invalid input with nothing cached still initializes, so later SDK calls don't throw.
    if (inputAppId != null) {
        return AppIdResolution(appId = inputAppId, forceCreateUser = true, failed = false)
    }

    return AppIdResolution(appId = null, forceCreateUser = false, failed = true)
}
