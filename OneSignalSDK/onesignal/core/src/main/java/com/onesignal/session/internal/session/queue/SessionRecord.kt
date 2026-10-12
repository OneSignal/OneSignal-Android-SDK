package com.onesignal.session.internal.session.queue

import com.onesignal.common.modeling.Model
import com.onesignal.common.modeling.SimpleModelStore
import com.onesignal.core.internal.preferences.IPreferencesService

/**
 * A session that still has something to send to the sessions API. There is one record per
 * session, keyed by the local session ID, so a newer update replaces an unsent one in place.
 */
internal class SessionRecord : Model() {
    var appId: String
        get() = getStringProperty(::appId.name)
        set(value) = setStringProperty(::appId.name, value)

    /** The session owner, pinned when the session started. Rewritten when a local ID is replaced. */
    var onesignalId: String
        get() = getStringProperty(::onesignalId.name)
        set(value) = setStringProperty(::onesignalId.name, value)

    var subscriptionId: String
        get() = getStringProperty(::subscriptionId.name)
        set(value) = setStringProperty(::subscriptionId.name, value)

    /** Unix time in seconds. */
    var startTime: Long
        get() = getLongProperty(::startTime.name)
        set(value) = setLongProperty(::startTime.name, value)

    var createKey: String
        get() = getStringProperty(::createKey.name)
        set(value) = setStringProperty(::createKey.name, value)

    /** Null until the create succeeds. */
    var serverSessionId: String?
        get() = getOptStringProperty(::serverSessionId.name)
        set(value) = setOptStringProperty(::serverSessionId.name, value)

    /** The unsent update, if any. Set together with [updateKey]. */
    var durationSeconds: Long?
        get() = getOptLongProperty(::durationSeconds.name)
        set(value) = setOptLongProperty(::durationSeconds.name, value)

    /** Unix time in seconds. Set when the unsent update ends the session. */
    var endTime: Long?
        get() = getOptLongProperty(::endTime.name)
        set(value) = setOptLongProperty(::endTime.name, value)

    var updateKey: String?
        get() = getOptStringProperty(::updateKey.name)
        set(value) = setOptStringProperty(::updateKey.name, value)

    /** Failures the backend answered, for the request being sent now. Network errors don't count. */
    var failedAttempts: Int
        get() = getIntProperty(::failedAttempts.name) { 0 }
        set(value) = setIntProperty(::failedAttempts.name, value)

    val hasEnded: Boolean
        get() = endTime != null
}

internal open class SessionRecordStore(prefs: IPreferencesService) :
    SimpleModelStore<SessionRecord>({ SessionRecord() }, "sessionsApiQueue", prefs)
