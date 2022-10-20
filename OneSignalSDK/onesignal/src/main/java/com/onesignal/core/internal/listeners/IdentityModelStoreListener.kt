package com.onesignal.core.internal.listeners

import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.models.IdentityModel
import com.onesignal.core.internal.models.IdentityModelStore
import com.onesignal.core.internal.operations.DeleteAliasOperation
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.operations.SetAliasOperation

internal class IdentityModelStoreListener(
    store: IdentityModelStore,
    opRepo: IOperationRepo,
    private val _configModelStore: ConfigModelStore
) : SingletonModelStoreListener<IdentityModel>(store, opRepo) {

    override fun getReplaceOperation(model: IdentityModel): Operation? {
        // when the identity model is replaced, nothing to do on the backend. Already handled via login process.
        return null
    }

    override fun getUpdateOperation(model: IdentityModel, path: String, property: String, oldValue: Any?, newValue: Any?): Operation {
        return if (newValue != null && newValue is String) {
            SetAliasOperation(_configModelStore.model.appId, model.onesignalId, property, newValue)
        } else {
            DeleteAliasOperation(_configModelStore.model.appId, model.onesignalId, property)
        }
    }
}
