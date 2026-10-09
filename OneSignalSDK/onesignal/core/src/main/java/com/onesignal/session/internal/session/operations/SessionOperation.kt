package com.onesignal.session.internal.session.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation

/**
 * Fields shared by [CreateSessionOperation] and [UpdateSessionOperation].
 */
abstract class SessionOperation(name: String) : Operation(name) {
    var appId: String
        get() = getStringProperty(::appId.name)
        protected set(value) {
            setStringProperty(::appId.name, value)
        }

    /**
     * May be a local ID; see [IDManager.isLocalId].
     */
    var onesignalId: String
        get() = getStringProperty(::onesignalId.name)
        protected set(value) {
            setStringProperty(::onesignalId.name, value)
        }

    /**
     * May be a local ID; see [IDManager.isLocalId].
     */
    var subscriptionId: String
        get() = getStringProperty(::subscriptionId.name)
        protected set(value) {
            setStringProperty(::subscriptionId.name, value)
        }

    /**
     * Generated once and persisted, so every retry, including after a restart, is deduplicated by the backend.
     */
    var idempotencyKey: String
        get() = getStringProperty(::idempotencyKey.name)
        protected set(value) {
            setStringProperty(::idempotencyKey.name, value)
        }

    /** Failed attempts the backend answered, persisted so the limit holds across restarts. */
    var failedAttempts: Int
        get() = getOptIntProperty(::failedAttempts.name) ?: 0
        internal set(value) {
            setIntProperty(::failedAttempts.name, value)
        }

    override val groupComparisonType: GroupComparisonType = GroupComparisonType.NONE
    override val requiresJwt: Boolean get() = false

    override fun translateIds(map: Map<String, String>) {
        map[onesignalId]?.let { onesignalId = it }
        map[subscriptionId]?.let { subscriptionId = it }
    }
}
