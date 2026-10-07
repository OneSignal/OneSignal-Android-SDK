package com.onesignal.session.internal.session.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.session.internal.session.operations.impl.SessionOperationExecutor
import java.util.UUID

/**
 * An [Operation] to report a session's cumulative duration through the sessions API, and to end
 * the session when [endTime] is set.
 */
class UpdateSessionOperation() : Operation(SessionOperationExecutor.UPDATE_SESSION) {
    var appId: String
        get() = getStringProperty(::appId.name)
        private set(value) {
            setStringProperty(::appId.name, value)
        }

    /**
     * The backend session ID, or the [CreateSessionOperation.localSessionId] until the create succeeds.
     */
    var sessionId: String
        get() = getStringProperty(::sessionId.name)
        private set(value) {
            setStringProperty(::sessionId.name, value)
        }

    var onesignalId: String
        get() = getStringProperty(::onesignalId.name)
        private set(value) {
            setStringProperty(::onesignalId.name, value)
        }

    var subscriptionId: String
        get() = getStringProperty(::subscriptionId.name)
        private set(value) {
            setStringProperty(::subscriptionId.name, value)
        }

    /** Cumulative active time in milliseconds. */
    var activeDuration: Long
        get() = getLongProperty(::activeDuration.name)
        internal set(value) {
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

    val isEnd: Boolean get() = endTime != null

    override val createComparisonKey: String get() = "$appId.Session.$sessionId"
    override val modifyComparisonKey: String get() = "$appId.Session.$sessionId"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.NONE
    override val canStartExecute: Boolean
        get() = !IDManager.isLocalId(sessionId) && !IDManager.isLocalId(onesignalId) && !IDManager.isLocalId(subscriptionId)
    override val applyToRecordId: String get() = sessionId
    override val requiresJwt: Boolean get() = false

    constructor(
        appId: String,
        sessionId: String,
        onesignalId: String,
        subscriptionId: String,
        activeDuration: Long,
        endTime: Long? = null,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) : this() {
        this.appId = appId
        this.sessionId = sessionId
        this.onesignalId = onesignalId
        this.subscriptionId = subscriptionId
        this.activeDuration = activeDuration
        this.endTime = endTime
        this.idempotencyKey = idempotencyKey
    }

    override fun translateIds(map: Map<String, String>) {
        map[sessionId]?.let { sessionId = it }
        map[onesignalId]?.let { onesignalId = it }
        map[subscriptionId]?.let { subscriptionId = it }
    }
}
