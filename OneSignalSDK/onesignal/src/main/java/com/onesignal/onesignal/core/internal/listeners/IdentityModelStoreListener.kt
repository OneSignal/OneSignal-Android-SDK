package com.onesignal.onesignal.core.internal.listeners

import com.onesignal.onesignal.core.internal.models.IdentityModel
import com.onesignal.onesignal.core.internal.models.IdentityModelStore
import com.onesignal.onesignal.core.internal.operations.CreateUserOperation
import com.onesignal.onesignal.core.internal.operations.DeleteUserOperation
import com.onesignal.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.onesignal.core.internal.operations.Operation
import com.onesignal.onesignal.core.internal.operations.UpdateUserOperation

internal class IdentityModelStoreListener(
    store: IdentityModelStore,
    opRepo: IOperationRepo
) : ModelStoreListener<IdentityModel>(store, opRepo) {

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
