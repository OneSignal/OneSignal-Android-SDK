package com.onesignal.user.internal.operations

import com.onesignal.common.IDManager
import com.onesignal.core.internal.operations.GroupComparisonType
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.customEvents.impl.CustomEvent
import com.onesignal.user.internal.operations.impl.executors.CustomEventOperationExecutor

class TrackEventOperation() : Operation(CustomEventOperationExecutor.CUSTOM_EVENT) {
    /**
     * The OneSignal appId the custom event was created.
     */
    var appId: String
        get() = getStringProperty(::appId.name)
        private set(value) {
            setStringProperty(::appId.name, value)
        }

    /**
     * The OneSignal ID the custom event was created under. This ID *may* be locally generated
     * and can be checked via [IDManager.isLocalId] to ensure correct processing.
     */
    var onesignalId: String
        get() = getStringProperty(::onesignalId.name)
        private set(value) {
            setStringProperty(::onesignalId.name, value)
        }

    /**
     * The optional external ID of current logged-in user. Must be unique for the [appId].
     */
    var externalId: String?
        get() = getOptStringProperty(::externalId.name)
        private set(value) {
            setOptStringProperty(::externalId.name, value)
        }

    /**
     * The timestamp when the custom event was created.
     */
    var timeStamp: Long
        get() = getLongProperty(::timeStamp.name)
        private set(value) {
            setLongProperty(::timeStamp.name, value)
        }

    /**
     * The custom event instance containing the event name and properties.
     */
    var event: CustomEvent
        get() = getAnyProperty(::event.name) as CustomEvent
        set(value) {
            setAnyProperty(::event.name, value)
        }

    override val createComparisonKey: String get() = "$appId.User.$onesignalId"
    override val modifyComparisonKey: String get() = "$appId.User.$onesignalId.CustomEvent.$name"

    // TODO: no batching of custom events until finalized
    override val groupComparisonType: GroupComparisonType = GroupComparisonType.NONE
    override val canStartExecute: Boolean get() = !IDManager.isLocalId(onesignalId)
    override val applyToRecordId: String get() = onesignalId

    constructor(appId: String, onesignalId: String, externalId: String?, timeStamp: Long, event: CustomEvent) : this() {
        this.appId = appId
        this.onesignalId = onesignalId
        this.externalId = externalId
        this.timeStamp = timeStamp
        this.event = event
    }

    override fun translateIds(map: Map<String, String>) {
        if (map.containsKey(onesignalId)) {
            onesignalId = map[onesignalId]!!
        }
    }
}
