package com.onesignal.onesignal.internal.listeners

import com.onesignal.onesignal.internal.backend.api.IApiService
import com.onesignal.onesignal.internal.modeling.IModelStore
import com.onesignal.onesignal.internal.models.IdentityModel
import com.onesignal.onesignal.internal.models.SubscriptionModel
import com.onesignal.onesignal.internal.operations.*

class SubscriptionModelStoreListener(
    store: IModelStore<SubscriptionModel>,
    opRepo: IOperationRepo,
    api: IApiService) : ModelStoreListener<SubscriptionModel>(store, opRepo, api){

    // TODO: IApiService shouldn't be here, not a dependency of the listener but need to create the backend operations. Need a factory? Maybe factor method in IOperationRepo?

    override fun getAddOperation(model: SubscriptionModel): Operation? {
        // TODO: Snapshot the model to prevent it from changing while the operation has been queued.
        return CreateSubscriptionOperation(api, model.id, model.type, model.address)
    }

    override fun getRemoveOperation(model: SubscriptionModel): Operation? {
        return DeleteSubscriptionOperation(api, model.id)
    }

    override fun getUpdateOperation(model: SubscriptionModel, property: String, oldValue: Any?, newValue: Any?): Operation? {
        return UpdateSubscriptionOperation(api, model.id, property, newValue)
    }
}
