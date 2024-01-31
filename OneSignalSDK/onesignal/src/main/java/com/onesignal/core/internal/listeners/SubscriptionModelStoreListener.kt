package com.onesignal.core.internal.listeners

import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.models.IdentityModelStore
import com.onesignal.core.internal.models.SubscriptionModel
import com.onesignal.core.internal.models.SubscriptionModelStore
import com.onesignal.core.internal.operations.CreateSubscriptionOperation
import com.onesignal.core.internal.operations.DeleteSubscriptionOperation
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.operations.UpdateSubscriptionOperation

internal class SubscriptionModelStoreListener(
    store: SubscriptionModelStore,
    opRepo: IOperationRepo,
    private val _identityModelStore: IdentityModelStore,
    private val _configModelStore: ConfigModelStore
) : ModelStoreListener<SubscriptionModel>(store, opRepo) {

    override fun getAddOperation(model: SubscriptionModel): Operation {
        return CreateSubscriptionOperation(_configModelStore.model.appId, _identityModelStore.model.onesignalId, model.id, model.type, model.enabled, model.address, model.status)
    }

    override fun getRemoveOperation(model: SubscriptionModel): Operation {
        return DeleteSubscriptionOperation(_configModelStore.model.appId, _identityModelStore.model.onesignalId, model.id)
    }

    override fun getUpdateOperation(model: SubscriptionModel, path: String, property: String, oldValue: Any?, newValue: Any?): Operation {
        return UpdateSubscriptionOperation(_configModelStore.model.appId, _identityModelStore.model.onesignalId, model.id, model.enabled, model.address, model.status)
    }
}
