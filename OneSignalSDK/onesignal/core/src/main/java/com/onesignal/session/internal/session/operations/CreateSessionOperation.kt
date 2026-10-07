package com.onesignal.session.internal.session.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.session.internal.session.operations.impl.SessionOperationExecutor
import java.util.UUID

/**
 * An [Operation] to create a session through the sessions API.
 */
class CreateSessionOperation() : Operation(SessionOperationExecutor.CREATE_SESSION) {
    var appId: String
        get() = getStringProperty(::appId.name)
        private set(value) {
            setStringProperty(::appId.name, value)
        }

    /**
     * Stands in for the backend session ID until this operation succeeds, so queued
     * [UpdateSessionOperation]s can reference the session and be translated.
     */
    var localSessionId: String
        get() = getStringProperty(::localSessionId.name)
        private set(value) {
            setStringProperty(::localSessionId.name, value)
        }

    /**
     * May be a local ID; see [IDManager.isLocalId].
     */
    var onesignalId: String
        get() = getStringProperty(::onesignalId.name)
        private set(value) {
            setStringProperty(::onesignalId.name, value)
        }

    /**
     * May be a local ID; see [IDManager.isLocalId].
     */
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

    var directAttributionId: String?
        get() = getOptStringProperty(::directAttributionId.name)
        private set(value) {
            setOptStringProperty(::directAttributionId.name, value)
        }

    /**
     * Generated once and persisted, so every retry, including after a restart, is deduplicated by the backend.
     */
    var idempotencyKey: String
        get() = getStringProperty(::idempotencyKey.name)
        private set(value) {
            setStringProperty(::idempotencyKey.name, value)
        }

    override val createComparisonKey: String get() = "$appId.Session.$localSessionId"
    override val modifyComparisonKey: String get() = "$appId.Session.$localSessionId"
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.NONE
    override val canStartExecute: Boolean get() = !IDManager.isLocalId(onesignalId) && !IDManager.isLocalId(subscriptionId)
    override val applyToRecordId: String get() = onesignalId
    override val requiresJwt: Boolean get() = false

    constructor(
        appId: String,
        localSessionId: String,
        onesignalId: String,
        subscriptionId: String,
        startTime: Long,
        directAttributionId: String? = null,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ) : this() {
        this.appId = appId
        this.localSessionId = localSessionId
        this.onesignalId = onesignalId
        this.subscriptionId = subscriptionId
        this.startTime = startTime
        this.directAttributionId = directAttributionId
        this.idempotencyKey = idempotencyKey
    }

    override fun translateIds(map: Map<String, String>) {
        map[onesignalId]?.let { onesignalId = it }
        map[subscriptionId]?.let { subscriptionId = it }
    }
}
