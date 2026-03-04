package com.onesignal.user.internal.identity

import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.common.modeling.SingletonModelStore
import com.onesignal.core.internal.preferences.IPreferencesService

const val IDENTITY_NAME_SPACE = "identity"

open class IdentityModelStore(prefs: IPreferencesService) : SingletonModelStore<IdentityModel>(
    SimpleModelStore({ IdentityModel() }, IDENTITY_NAME_SPACE, prefs),
)
