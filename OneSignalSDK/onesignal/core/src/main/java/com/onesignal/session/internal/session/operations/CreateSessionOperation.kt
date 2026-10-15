package com.onesignal.session.internal.session.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.session.internal.session.operations.impl.SessionOperationExecutor

/**
 * An [Operation] to create a session through the sessions API.
 */
internal class CreateSessionOperation() : Operation(SessionOperationExecutor.CREATE_SESSION) {
    var appId: String
        get() = getStringProperty(::appId.name)
        private set(value) {
            setStringProperty(::appId.name, value)
        }

    /**
     * Stands in for the backend session ID until the create succeeds. Updates queued before then
     * reference it and are translated to the backend ID.
     */
    var localSessionId: String
        get() = getStringProperty(::localSessionId.name)
        private set(value) {
            setStringProperty(::localSessionId.name, value)
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

    /** Unix time in milliseconds. */
    var startTime: Long
        get() = getLongProperty(::startTime.name)
        private set(value) {
            setLongProperty(::startTime.name, value)
        }

    /** Stable across retries and restarts, so the backend recognizes a resent create. */
    val idempotencyKey: String get() = localSessionId

    /** Responses so far that had no session_id. */
    var missingSessionIdAttempts: Int
        get() = getOptIntProperty(::missingSessionIdAttempts.name) ?: 0
        internal set(value) {
            setIntProperty(::missingSessionIdAttempts.name, value)
        }

    override val requiresJwt: Boolean get() = false
    override val createComparisonKey: String get() = ""
    override val modifyComparisonKey: String get() = ""
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.NONE
    override val canStartExecute: Boolean
        get() = !IDManager.isLocalId(onesignalId) && !IDManager.isLocalId(subscriptionId)
    override val applyToRecordId: String get() = localSessionId

    constructor(
        appId: String,
        localSessionId: String,
        onesignalId: String,
        subscriptionId: String,
        startTime: Long,
        externalId: String?,
    ) : this() {
        this.appId = appId
        this.localSessionId = localSessionId
        this.onesignalId = onesignalId
        this.subscriptionId = subscriptionId
        this.startTime = startTime
        this.externalId = externalId
    }

    override fun translateIds(map: Map<String, String>) {
        map[onesignalId]?.let { onesignalId = it }
        map[subscriptionId]?.let { subscriptionId = it }
    }
}
