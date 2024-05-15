package com.onesignal.user.internal.identity

import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.common.modeling.SingletonModelStore
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.user.internal.backend.IdentityConstants

open class IdentityModelStore(prefs: IPreferencesService) : SingletonModelStore<IdentityModel>(
    SimpleModelStore({ IdentityModel() }, "identity", prefs),
) {
    fun invalidateJwt() {
        model.setStringProperty(
            IdentityConstants.JWT_TOKEN,
            "",
        )
    }
}
