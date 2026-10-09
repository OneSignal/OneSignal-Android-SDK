package com.onesignal.session.internal.session

import com.onesignal.common.IDManager
import com.onesignal.common.modeling.Model

/**
 * The session model tracks all data related to the SDK's current session.
 */
class SessionModel : Model() {
    /**
     * The ID of the current session.
     */
    var sessionId: String
        get() = getStringProperty(::sessionId.name)
        set(value) {
            setStringProperty(::sessionId.name, value)
        }

    /**
     * Indicates if there is an active session.
     * True when app is in the foreground.
     * Also true in the background for a short period of time (default 30s)
     * as a debouncing mechanism.
     */
    var isValid: Boolean
        get() = getBooleanProperty(::isValid.name) { false }
        set(value) {
            setBooleanProperty(::isValid.name, value)
        }

    /**
     * When this session started, in Unix time milliseconds.
     * This is used by In-App Message triggers, and not used in detecting session time.
     */
    var startTime: Long
        get() = getLongProperty(::startTime.name) { System.currentTimeMillis() }
        set(value) {
            setLongProperty(::startTime.name, value)
        }

    /**
     * When this app was last focused, in Unix time milliseconds.
     */
    var focusTime: Long
        get() = getLongProperty(::focusTime.name) { System.currentTimeMillis() }
        set(value) {
            setLongProperty(::focusTime.name, value)
        }

    /**
     * When this app was last unfocused, in Unix time milliseconds. Null until the first unfocus.
     */
    var unfocusTime: Long?
        get() = getOptLongProperty(::unfocusTime.name)
        set(value) {
            setOptLongProperty(::unfocusTime.name, value)
        }

    /**
     * How long this session has spent as active, in milliseconds.
     */
    var activeDuration: Long
        get() = getLongProperty(::activeDuration.name) { 0L }
        set(value) {
            setLongProperty(::activeDuration.name, value)
        }

    /**
     * [focusTime] from a monotonic clock ([com.onesignal.core.internal.time.ITime.elapsedRealtimeMillis]).
     * Used instead of [focusTime] to measure [activeDuration] when [usesSessionsApi].
     */
    var focusElapsedRealtime: Long
        get() = getLongProperty(::focusElapsedRealtime.name) { 0L }
        set(value) {
            setLongProperty(::focusElapsedRealtime.name, value)
        }

    /**
     * Whether this session reports through the sessions API instead of the legacy session paths.
     * Decided by [com.onesignal.features.FeatureFlag.SDK_SESSIONS_V2_API_CUTOVER] when the session
     * starts and fixed until it ends, so a session never mixes paths. Read this rather than the
     * feature manager, whose value can change mid-session.
     */
    var usesSessionsApi: Boolean
        get() = getBooleanProperty(::usesSessionsApi.name) { false }
        set(value) {
            setBooleanProperty(::usesSessionsApi.name, value)
        }

    /**
     * The OneSignal ID when this session started. Session updates keep using it after a
     * login or user switch.
     */
    var onesignalId: String?
        get() = getOptStringProperty(::onesignalId.name)
        set(value) {
            setOptStringProperty(::onesignalId.name, value)
        }

    /**
     * The push subscription ID when this session started. Pinned like [onesignalId].
     */
    var subscriptionId: String?
        get() = getOptStringProperty(::subscriptionId.name)
        set(value) {
            setOptStringProperty(::subscriptionId.name, value)
        }

    /**
     * Null until the sessions API creates this session. Cleared when a new session starts.
     */
    var serverSessionId: String?
        get() = getOptStringProperty(::serverSessionId.name)
        set(value) {
            setOptStringProperty(::serverSessionId.name, value)
        }

    /**
     * Stands in for [serverSessionId] in queued session operations until the backend assigns one.
     */
    internal val localSessionId: String
        get() = "${IDManager.LOCAL_PREFIX}$sessionId"

    /**
     * Applies the same backend IDs the operation queue gives this session's operations, so
     * updates built from these fields, including after a restart, match them.
     */
    internal fun translateIds(map: Map<String, String>) {
        // Unset until the first session starts.
        if (!hasProperty(::sessionId.name)) return

        map[localSessionId]?.let { serverSessionId = it }
        onesignalId = onesignalId?.let { map[it] } ?: return
        // Login reuses a local push subscription for the new user, so its ID only belongs to this
        // session when the same response created this session's user.
        subscriptionId?.let { map[it] }?.let { subscriptionId = it }
    }
}
