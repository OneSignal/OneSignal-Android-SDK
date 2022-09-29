package com.onesignal.core.internal.listeners

import com.onesignal.core.internal.models.ConfigModelStore
import com.onesignal.core.internal.models.PropertiesModel
import com.onesignal.core.internal.models.PropertiesModelStore
import com.onesignal.core.internal.operations.DeleteTagOperation
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.operations.SetPropertyOperation
import com.onesignal.core.internal.operations.SetTagOperation

internal class PropertiesModelStoreListener(
    store: PropertiesModelStore,
    opRepo: IOperationRepo,
    private val _configModelStore: ConfigModelStore
) : SingletonModelStoreListener<PropertiesModel>(store, opRepo) {

    override fun getReplaceOperation(model: PropertiesModel): Operation? {
        // when the property model is replaced, nothing to do on the backend. Already handled via login process.
        return null
    }

    override fun getUpdateOperation(model: PropertiesModel, path: String, property: String, oldValue: Any?, newValue: Any?): Operation? {
        if (path.startsWith(PropertiesModel::tags.name)) {
            return if (newValue != null && newValue is String) {
                SetTagOperation(_configModelStore.get().appId, model.onesignalId, property, newValue)
            } else {
                DeleteTagOperation(_configModelStore.get().appId, model.onesignalId, property)
            }
        }

        return SetPropertyOperation(_configModelStore.get().appId, model.onesignalId, property, newValue)
    }
}
