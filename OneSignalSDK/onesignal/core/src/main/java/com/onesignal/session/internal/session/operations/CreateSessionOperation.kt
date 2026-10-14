package com.onesignal.session.internal.session.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.session.internal.session.operations.impl.SessionOperationExecutor
import java.util.UUID

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

    /** Persisted so a retry after a restart is recognized by the backend as the same request. */
    var idempotencyKey: String
        get() = getStringProperty(::idempotencyKey.name)
        private set(value) {
            setStringProperty(::idempotencyKey.name, value)
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
    ) : this() {
        this.appId = appId
        this.localSessionId = localSessionId
        this.onesignalId = onesignalId
        this.subscriptionId = subscriptionId
        this.startTime = startTime
        this.idempotencyKey = UUID.randomUUID().toString()
    }

    override fun translateIds(map: Map<String, String>) {
        map[onesignalId]?.let { onesignalId = it }
        map[subscriptionId]?.let { subscriptionId = it }
    }
}
