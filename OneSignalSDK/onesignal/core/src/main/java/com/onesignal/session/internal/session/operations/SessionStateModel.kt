package com.onesignal.session.internal.session.operations

import com.onesignal.common.modeling.Model
import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.core.internal.preferences.IPreferencesService

/**
 * The one place that knows whether a session was created and whether it ended. Persisted, since a
 * session's operations can leave the queue, or the process can restart, while more of its updates
 * are still coming. [id] is the session's local ID, or its backend ID when only that is known.
 */
internal class SessionStateModel : Model() {
    /** Set once the create succeeds. */
    var serverSessionId: String?
        get() = getOptStringProperty(::serverSessionId.name)
        set(value) {
            setOptStringProperty(::serverSessionId.name, value)
        }

    /** The user the create is sent with, which every update repeats. Null when no create is known. */
    var onesignalId: String?
        get() = getOptStringProperty(::onesignalId.name)
        set(value) {
            setOptStringProperty(::onesignalId.name, value)
        }

    /** The subscription the create is sent with, which every update repeats. */
    var subscriptionId: String?
        get() = getOptStringProperty(::subscriptionId.name)
        set(value) {
            setOptStringProperty(::subscriptionId.name, value)
        }

    /** Takes no more updates: it ended, or its create was dropped. */
    var isClosed: Boolean
        get() = getBooleanProperty(::isClosed.name) { false }
        set(value) {
            setBooleanProperty(::isClosed.name, value)
        }

    fun translateIds(map: Map<String, String>) {
        if (serverSessionId == null) map[id]?.let { serverSessionId = it }
        onesignalId?.let { map[it] }?.let { onesignalId = it }
        subscriptionId?.let { map[it] }?.let { subscriptionId = it }
    }
}

internal class SessionStateModelStore(prefs: IPreferencesService) :
    SimpleModelStore<SessionStateModel>({ SessionStateModel() }, "sessionsApiState", prefs)
