package com.onesignal.user.internal.identity

import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.common.modeling.SingletonModelStore
import com.onesignal.core.internal.preferences.IPreferencesService

open class IdentityModelStore(prefs: IPreferencesService) : SingletonModelStore<IdentityModel>(
    SimpleModelStore({ IdentityModel() }, "identity", prefs),
) {
    fun invalidateJwt() {
        model.jwtToken = ""
    }
}
