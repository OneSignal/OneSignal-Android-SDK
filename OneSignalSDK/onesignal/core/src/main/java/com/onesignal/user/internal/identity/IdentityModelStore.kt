package com.onesignal.user.internal.identity

import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.common.modeling.SingletonModelStore
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.user.internal.backend.IdentityConstants

open class IdentityModelStore(prefs: IPreferencesService) : SingletonModelStore<IdentityModel>(
    SimpleModelStore({ IdentityModel() }, "identity", prefs),
) {
    fun invalidateJwt() {
        model.jwtToken = null
    }

    // Use externalId instead of onesignalId when a jwt is present
    fun getIdentityAlias(): Pair<String, String> {
        if (model.jwtToken == null) {
            return Pair(IdentityConstants.ONESIGNAL_ID, model.onesignalId)
        }

        return Pair(IdentityConstants.EXTERNAL_ID, model.externalId!!)
    }
}
