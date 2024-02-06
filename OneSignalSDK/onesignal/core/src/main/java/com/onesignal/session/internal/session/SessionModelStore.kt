package com.onesignal.session.internal.session

import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.common.modeling.SingletonModelStore
import com.onesignal.core.internal.preferences.IPreferencesService

open class SessionModelStore(prefs: IPreferencesService) : SingletonModelStore<SessionModel>(
    SimpleModelStore({ SessionModel() }, "session", prefs),
)
