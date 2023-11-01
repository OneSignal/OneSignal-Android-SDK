package com.onesignal.user.internal.operations.impl.listeners

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.operations.listeners.SingletonModelStoreListener
import com.onesignal.user.internal.operations.DeleteTagOperation
import com.onesignal.user.internal.operations.SetPropertyOperation
import com.onesignal.user.internal.operations.SetTagOperation
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore

internal class PropertiesModelStoreListener(
    store: PropertiesModelStore,
    opRepo: IOperationRepo,
    private val _configModelStore: ConfigModelStore,
) : SingletonModelStoreListener<PropertiesModel>(store, opRepo) {
    override fun getReplaceOperation(model: PropertiesModel): Operation? {
        // when the property model is replaced, nothing to do on the backend. Already handled via login process.
        return null
    }

    override fun getUpdateOperation(
        model: PropertiesModel,
        path: String,
        property: String,
        oldValue: Any?,
        newValue: Any?,
    ): Operation? {
        // for any of the property changes, we do not need to fire an operation.
        if (path.startsWith(PropertiesModel::locationTimestamp.name) ||
            path.startsWith(PropertiesModel::locationBackground.name) ||
            path.startsWith(PropertiesModel::locationType.name) ||
            path.startsWith(PropertiesModel::locationAccuracy.name)
        ) {
            return null
        }

        if (path.startsWith(PropertiesModel::tags.name)) {
            return if (newValue != null && newValue is String) {
                SetTagOperation(_configModelStore.model.appId, model.onesignalId, property, newValue)
            } else {
                DeleteTagOperation(_configModelStore.model.appId, model.onesignalId, property)
            }
        }

        return SetPropertyOperation(_configModelStore.model.appId, model.onesignalId, property, newValue)
    }
}
