package com.onesignal.onesignal.internal.listeners

import com.onesignal.onesignal.internal.backend.api.IApiService
import com.onesignal.onesignal.internal.modeling.IModelStore
import com.onesignal.onesignal.internal.models.IdentityModel
import com.onesignal.onesignal.internal.operations.*

class IdentityModelStoreListener(
    store: IModelStore<IdentityModel>,
    opRepo: IOperationRepo,
    api: IApiService) : ModelStoreListener<IdentityModel>(store, opRepo, api){

    // TODO: IApiService shouldn't be here, not a dependency of the listener but need to create the backend operations. Need a factory? Maybe factor method in IOperationRepo?

    override fun getAddOperation(model: IdentityModel): Operation? {
        // TODO: Snapshot the model to prevent it from changing while the operation has been queued.
        return CreateUserOperation(api, model.id)
    }

    override fun getRemoveOperation(model: IdentityModel): Operation? {
        return DeleteUserOperation(api, model.id)
    }

    override fun getUpdateOperation(model: IdentityModel, property: String, oldValue: Any?, newValue: Any?): Operation? {
        return UpdateUserOperation(api, model.id, property, newValue)
    }
}
