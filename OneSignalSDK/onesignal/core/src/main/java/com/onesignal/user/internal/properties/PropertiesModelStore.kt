package com.onesignal.user.internal.properties

import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.common.modeling.SingletonModelStore
import com.onesignal.core.internal.preferences.IPreferencesService

open class PropertiesModelStore(prefs: IPreferencesService) : SingletonModelStore<PropertiesModel>(
    SimpleModelStore({ PropertiesModel() }, "properties", prefs),
)
