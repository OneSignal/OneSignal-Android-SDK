package com.onesignal.core.internal.listeners

import com.onesignal.core.internal.models.SubscriptionModel
import com.onesignal.core.internal.models.SubscriptionModelStore
import com.onesignal.core.internal.operations.CreateSubscriptionOperation
import com.onesignal.core.internal.operations.DeleteSubscriptionOperation
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.operations.UpdateSubscriptionOperation
import com.onesignal.core.internal.params.IParamsService

internal class SubscriptionModelStoreListener(
    store: SubscriptionModelStore,
    private val _params: IParamsService,
    opRepo: IOperationRepo
) : ModelStoreListener<SubscriptionModel>(store, opRepo) {

    override fun getAddOperation(model: SubscriptionModel): Operation? {
        return CreateSubscriptionOperation(_params.appId!!, model.id, model.type, model.enabled, model.address)
    }

    override fun getRemoveOperation(model: SubscriptionModel): Operation? {
        return DeleteSubscriptionOperation(model.id)
    }

    override fun getUpdateOperation(model: SubscriptionModel, property: String, oldValue: Any?, newValue: Any?): Operation? {
        return UpdateSubscriptionOperation(model.id, property, newValue)
    }
}
