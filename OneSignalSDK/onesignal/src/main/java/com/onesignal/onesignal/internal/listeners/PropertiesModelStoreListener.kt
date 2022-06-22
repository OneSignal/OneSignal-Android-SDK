package com.onesignal.onesignal.internal.listeners

import com.onesignal.onesignal.internal.backend.api.IApiService
import com.onesignal.onesignal.internal.modeling.IModelStore
import com.onesignal.onesignal.internal.models.IdentityModel
import com.onesignal.onesignal.internal.models.PropertiesModel
import com.onesignal.onesignal.internal.operations.*

class PropertiesModelStoreListener(
    store: IModelStore<PropertiesModel>,
    opRepo: IOperationRepo,
    api: IApiService) : ModelStoreListener<PropertiesModel>(store, opRepo, api){

    // TODO: IApiService shouldn't be here, not a dependency of the listener but need to create the backend operations. Need a factory? Maybe factor method in IOperationRepo?

    override fun getAddOperation(model: PropertiesModel): Operation? {
        // when a property model is added, nothing to do on the backend
        return null;
    }

    override fun getRemoveOperation(model: PropertiesModel): Operation? {
        // when a property model is removed, nothing to do on the backend
        return null;
    }

    override fun getUpdateOperation(model: PropertiesModel, property: String, oldValue: Any?, newValue: Any?): Operation? {
        return UpdatePropertyOperation(api, model.id, property, newValue)
    }
}
