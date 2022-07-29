package com.onesignal.onesignal.core.internal.listeners

import com.onesignal.onesignal.core.internal.models.PropertiesModel
import com.onesignal.onesignal.core.internal.models.PropertiesModelStore
import com.onesignal.onesignal.core.internal.operations.*

internal class PropertiesModelStoreListener(
    store: PropertiesModelStore,
    opRepo: IOperationRepo) : ModelStoreListener<PropertiesModel>(store, opRepo){

    override fun getAddOperation(model: PropertiesModel): Operation? {
        // when a property model is added, nothing to do on the backend
        return null;
    }

    override fun getRemoveOperation(model: PropertiesModel): Operation? {
        // when a property model is removed, nothing to do on the backend
        return null;
    }

    override fun getUpdateOperation(model: PropertiesModel, property: String, oldValue: Any?, newValue: Any?): Operation? {
        return UpdatePropertyOperation(model.id, property, newValue)
    }
}
