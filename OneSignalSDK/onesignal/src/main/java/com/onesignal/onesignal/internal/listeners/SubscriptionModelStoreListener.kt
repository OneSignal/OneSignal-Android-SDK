package com.onesignal.onesignal.internal.listeners

import com.onesignal.onesignal.internal.backend.api.IApiService
import com.onesignal.onesignal.internal.modeling.IModelStore
import com.onesignal.onesignal.internal.models.IdentityModel
import com.onesignal.onesignal.internal.operations.*

class SubscriptionModelStoreListener(
    store: IModelStore<IdentityModel>,
    opRepo: IOperationRepo,
    api: IApiService) : ModelStoreListener<IdentityModel>(store, opRepo, api){

    // TODO: IApiService shouldn't be here, not a dependency of the listener but need to create the backend operations. Need a factory? Maybe factor method in IOperationRepo?

    override fun getOperation(action: Action, id: String, model: IdentityModel): Operation? {
        return when(action) {
            Action.CREATED -> CreateSubscriptionOperation(api)
            Action.UPDATED -> UpdateSubscriptionOperation(api, id)
            Action.DELETED -> DeleteSubscriptionOperation(api, id)
        }
    }
}
