package com.onesignal.user.internal.operations.impl.listeners

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.operations.listeners.ModelStoreListener
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.CreateSubscriptionOperation
import com.onesignal.user.internal.operations.DeleteSubscriptionOperation
import com.onesignal.user.internal.operations.UpdateSubscriptionOperation
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus

internal class SubscriptionModelStoreListener(
    store: SubscriptionModelStore,
    opRepo: IOperationRepo,
    private val _identityModelStore: IdentityModelStore,
    private val _configModelStore: ConfigModelStore
) : ModelStoreListener<SubscriptionModel>(store, opRepo) {

    override fun getAddOperation(model: SubscriptionModel): Operation {
        val status: SubscriptionStatus
        val enabled: Boolean

        if (model.optedIn && model.status == SubscriptionStatus.SUBSCRIBED && model.address.isNotEmpty()) {
            enabled = true
            status = SubscriptionStatus.SUBSCRIBED
        } else {
            enabled = false
            status = if (!model.optedIn) {
                SubscriptionStatus.UNSUBSCRIBE
            } else {
                model.status
            }
        }

        return CreateSubscriptionOperation(_configModelStore.model.appId, _identityModelStore.model.onesignalId, model.id, model.type, enabled, model.address, status)
    }

    override fun getRemoveOperation(model: SubscriptionModel): Operation {
        return DeleteSubscriptionOperation(_configModelStore.model.appId, _identityModelStore.model.onesignalId, model.id)
    }

    override fun getUpdateOperation(model: SubscriptionModel, path: String, property: String, oldValue: Any?, newValue: Any?): Operation {
        val status: SubscriptionStatus
        val enabled: Boolean

        if (model.optedIn && model.status == SubscriptionStatus.SUBSCRIBED && model.address.isNotEmpty()) {
            enabled = true
            status = SubscriptionStatus.SUBSCRIBED
        } else {
            enabled = false
            status = if (!model.optedIn) {
                SubscriptionStatus.UNSUBSCRIBE
            } else {
                model.status
            }
        }

        return UpdateSubscriptionOperation(_configModelStore.model.appId, _identityModelStore.model.onesignalId, model.id, model.type, enabled, model.address, status)
    }
}
