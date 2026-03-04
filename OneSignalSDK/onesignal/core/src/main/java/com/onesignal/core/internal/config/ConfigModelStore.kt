package com.onesignal.core.internal.config

import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.common.modeling.SingletonModelStore
import com.onesignal.core.internal.preferences.IPreferencesService

open class ConfigModelStore(prefs: IPreferencesService) : SingletonModelStore<ConfigModel>(
    SimpleModelStore({ ConfigModel() }, "config", prefs),
)
