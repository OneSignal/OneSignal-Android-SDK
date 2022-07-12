package com.onesignal.onesignal.core.internal.listeners

import com.onesignal.onesignal.core.internal.modeling.IModelStore
import com.onesignal.onesignal.core.internal.models.SubscriptionModel
import com.onesignal.onesignal.core.internal.operations.*

class SubscriptionModelStoreListener(
    store: IModelStore<SubscriptionModel>,
    opRepo: IOperationRepo) : ModelStoreListener<SubscriptionModel>(store, opRepo){

    override fun getAddOperation(model: SubscriptionModel): Operation? {
        // TODO: Snapshot the model to prevent it from changing while the operation has been queued.
        return CreateSubscriptionOperation(model.id, model.type, model.address)
    }

    override fun getRemoveOperation(model: SubscriptionModel): Operation? {
        return DeleteSubscriptionOperation(model.id)
    }

    override fun getUpdateOperation(model: SubscriptionModel, property: String, oldValue: Any?, newValue: Any?): Operation? {
        return UpdateSubscriptionOperation(model.id, property, newValue)
    }
}
