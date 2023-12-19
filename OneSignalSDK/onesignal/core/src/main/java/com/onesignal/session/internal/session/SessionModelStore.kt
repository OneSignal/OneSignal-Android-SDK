package com.onesignal.session.internal.session

import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.common.modeling.SingletonModelStore
import com.onesignal.core.internal.preferences.IPreferencesService

open class SessionModelStore(prefs: IPreferencesService) : SingletonModelStore<SessionModel>(
    SimpleModelStore(
        { SessionModel() },
        "session",
        prefs,
    ),
) {
    /**
     * Called after the underlying ModelStore is initialized.
     * On cold starts, reset the session validity property to drive a new session.
     */
    init {
        println("❌ SessionModelStore init called with models ${store.list()}")
        if (!store.list().isEmpty()) {
            println("❌ SessionModelStore NOT EMPTY}")
            model.isValid = false
        }
    }
}
