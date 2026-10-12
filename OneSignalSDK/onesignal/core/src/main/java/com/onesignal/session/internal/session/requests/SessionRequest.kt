package com.onesignal.session.internal.session.requests

import com.onesignal.common.IDManager
import com.onesignal.common.modeling.Model
import java.util.UUID

/**
 * A persisted sessions API request, waiting in [ISessionRequestQueue] to be sent.
 */
internal class SessionRequest : Model() {
    enum class Type { CREATE, UPDATE }

    var type: Type
        get() = getEnumProperty(::type.name)
        private set(value) {
            setEnumProperty(::type.name, value)
        }

    var appId: String
        get() = getStringProperty(::appId.name)
        private set(value) {
            setStringProperty(::appId.name, value)
        }

    /**
     * The SDK's [com.onesignal.session.internal.session.SessionModel.sessionId], which ties a
     * session's updates to its create.
     */
    var localSessionId: String
        get() = getStringProperty(::localSessionId.name)
        private set(value) {
            setStringProperty(::localSessionId.name, value)
        }

    /**
     * Null until the session's create succeeds. Always null for a create.
     */
    var serverSessionId: String?
        get() = getOptStringProperty(::serverSessionId.name)
        set(value) {
            setOptStringProperty(::serverSessionId.name, value)
        }

    /**
     * May be a local ID until the user is created; see [IDManager.isLocalId].
     */
    var onesignalId: String
        get() = getStringProperty(::onesignalId.name)
        set(value) {
            setStringProperty(::onesignalId.name, value)
        }

    /**
     * May be a local ID until the subscription is created; see [IDManager.isLocalId].
     */
    var subscriptionId: String
        get() = getStringProperty(::subscriptionId.name)
        set(value) {
            setStringProperty(::subscriptionId.name, value)
        }

    /** Unix time in milliseconds. Set on a create. */
    var startTime: Long
        get() = getLongProperty(::startTime.name) { 0L }
        private set(value) {
            setLongProperty(::startTime.name, value)
        }

    /** Cumulative active time in milliseconds. Set on an update. */
    var activeDuration: Long
        get() = getLongProperty(::activeDuration.name) { 0L }
        set(value) {
            setLongProperty(::activeDuration.name, value)
        }

    /** Unix time in milliseconds; set when this update ends the session. */
    var endTime: Long?
        get() = getOptLongProperty(::endTime.name)
        private set(value) {
            setOptLongProperty(::endTime.name, value)
        }

    /**
     * Generated once and persisted, so every retry, including after a restart, is deduplicated by the backend.
     */
    var idempotencyKey: String
        get() = getStringProperty(::idempotencyKey.name)
        private set(value) {
            setStringProperty(::idempotencyKey.name, value)
        }

    /** Failed attempts the backend answered, persisted so the limit holds across restarts. */
    var failedAttempts: Int
        get() = getOptIntProperty(::failedAttempts.name) ?: 0
        set(value) {
            setIntProperty(::failedAttempts.name, value)
        }

    val isCreate: Boolean get() = type == Type.CREATE

    val isEnd: Boolean get() = type == Type.UPDATE && endTime != null

    val hasBackendIds: Boolean
        get() = !IDManager.isLocalId(onesignalId) && !IDManager.isLocalId(subscriptionId)

    fun isSameSession(other: SessionRequest) = appId == other.appId && localSessionId == other.localSessionId

    override fun toString() = "SessionRequest(type: $type, session: $localSessionId, end: $isEnd, failedAttempts: $failedAttempts)"

    companion object {
        fun create(
            appId: String,
            localSessionId: String,
            onesignalId: String,
            subscriptionId: String,
            startTime: Long,
        ) = SessionRequest().apply {
            init(Type.CREATE, appId, localSessionId, onesignalId, subscriptionId)
            this.startTime = startTime
        }

        @Suppress("LongParameterList")
        fun update(
            appId: String,
            localSessionId: String,
            serverSessionId: String?,
            onesignalId: String,
            subscriptionId: String,
            activeDuration: Long,
            endTime: Long? = null,
        ) = SessionRequest().apply {
            init(Type.UPDATE, appId, localSessionId, onesignalId, subscriptionId)
            this.serverSessionId = serverSessionId
            this.activeDuration = activeDuration
            this.endTime = endTime
        }

        private fun SessionRequest.init(
            type: Type,
            appId: String,
            localSessionId: String,
            onesignalId: String,
            subscriptionId: String,
        ) {
            id = UUID.randomUUID().toString()
            this.type = type
            this.appId = appId
            this.localSessionId = localSessionId
            this.onesignalId = onesignalId
            this.subscriptionId = subscriptionId
            idempotencyKey = UUID.randomUUID().toString()
        }
    }
}
