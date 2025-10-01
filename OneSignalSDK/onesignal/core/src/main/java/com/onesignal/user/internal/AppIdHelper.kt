package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.getLegacyAppId

data class AppIdResolution(
    val appId: String?, // nullable
    val forceCreateUser: Boolean,
    val failed: Boolean
)

fun resolveAppId(
    inputAppId: String?,
    configModel: ConfigModel,
    preferencesService: IPreferencesService
): AppIdResolution {
    var forceCreateUser = false
    var resolvedAppId: String? = inputAppId

    if (inputAppId != null) {
        if (!configModel.hasProperty(ConfigModel::appId.name) || configModel.appId != inputAppId) {
            forceCreateUser = true
        }
    } else {
        if (!configModel.hasProperty(ConfigModel::appId.name)) {
            val legacyAppId = preferencesService.getLegacyAppId()
            if (legacyAppId == null) {
                return AppIdResolution(null, false, true)
            }
            forceCreateUser = true
            resolvedAppId = legacyAppId
        }
    }
    return AppIdResolution(resolvedAppId, forceCreateUser, false)
}
