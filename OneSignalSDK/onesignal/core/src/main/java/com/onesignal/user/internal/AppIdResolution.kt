package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.getLegacyAppId

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
    // Case 1: AppId provided as input
    if (inputAppId != null) {
        val forceCreateUser = !configModel.hasProperty(ConfigModel::appId.name) || configModel.appId != inputAppId
        return AppIdResolution(appId = inputAppId, forceCreateUser = forceCreateUser, failed = false)
    }

    // Case 2: No appId provided, but configModel has one
    if (configModel.hasProperty(ConfigModel::appId.name)) {
        return AppIdResolution(appId = configModel.appId, forceCreateUser = false, failed = false)
    }

    // Case 3: No appId provided, no configModel appId - try legacy
    val legacyAppId = preferencesService.getLegacyAppId()
    if (legacyAppId != null) {
        return AppIdResolution(appId = legacyAppId, forceCreateUser = true, failed = false)
    }

    return AppIdResolution(appId = null, forceCreateUser = false, failed = true)
}
