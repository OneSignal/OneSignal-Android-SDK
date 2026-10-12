package com.onesignal.session.internal.session.requests

import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.core.internal.preferences.IPreferencesService

internal open class SessionRequestStore(prefs: IPreferencesService) :
    SimpleModelStore<SessionRequest>({ SessionRequest() }, "session_requests", prefs)
