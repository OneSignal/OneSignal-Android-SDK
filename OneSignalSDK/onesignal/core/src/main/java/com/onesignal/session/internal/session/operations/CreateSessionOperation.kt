package com.onesignal.session.internal.session.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.Operation
import com.onesignal.session.internal.session.operations.impl.SessionOperationExecutor
import java.util.UUID

/**
 * An [Operation] to create a session through the sessions API.
 */
class CreateSessionOperation() : SessionOperation(SessionOperationExecutor.CREATE_SESSION) {
    /**
     * Stands in for the backend session ID until this operation succeeds, so queued
     * [UpdateSessionOperation]s can reference the session and be translated.
     */
    var localSessionId: String
        get() = getStringProperty(::localSessionId.name)
        private set(value) {
            setStringProperty(::localSessionId.name, value)
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

    override val createComparisonKey: String get() = "$appId.Session.$localSessionId"
    override val modifyComparisonKey: String get() = "$appId.Session.$localSessionId"
    override val canStartExecute: Boolean get() = !IDManager.isLocalId(onesignalId) && !IDManager.isLocalId(subscriptionId)
    override val applyToRecordId: String get() = onesignalId

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
}
