package com.onesignal.onesignal.core.internal.listeners

import com.onesignal.onesignal.core.internal.modeling.IModelStore
import com.onesignal.onesignal.core.internal.models.IdentityModel
import com.onesignal.onesignal.core.internal.operations.*

class IdentityModelStoreListener(
    store: IModelStore<IdentityModel>,
    opRepo: IOperationRepo) : ModelStoreListener<IdentityModel>(store, opRepo){

    override fun getAddOperation(model: IdentityModel): Operation? {
        // TODO: Snapshot the model to prevent it from changing while the operation has been queued.
        return CreateUserOperation(model.id)
    }

    override fun getRemoveOperation(model: IdentityModel): Operation? {
        return DeleteUserOperation(model.id)
    }

    override fun getUpdateOperation(model: IdentityModel, property: String, oldValue: Any?, newValue: Any?): Operation? {
        return UpdateUserOperation(model.id, property, newValue)
    }
}
