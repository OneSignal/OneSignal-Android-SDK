package com.onesignal.core.internal.operations.impl

import com.onesignal.core.internal.modeling.ModelStore
import com.onesignal.core.internal.operations.CreateSubscriptionOperation
import com.onesignal.core.internal.operations.CreateUserOperation
import com.onesignal.core.internal.operations.DeleteAliasOperation
import com.onesignal.core.internal.operations.DeleteSubscriptionOperation
import com.onesignal.core.internal.operations.DeleteTagOperation
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.operations.SetAliasOperation
import com.onesignal.core.internal.operations.SetPropertyOperation
import com.onesignal.core.internal.operations.SetTagOperation
import com.onesignal.core.internal.operations.TrackPurchaseOperation
import com.onesignal.core.internal.operations.TrackSessionOperation
import com.onesignal.core.internal.operations.UpdateSubscriptionOperation
import com.onesignal.core.internal.preferences.IPreferencesService
import org.json.JSONObject

internal class OperationModelStore(prefs: IPreferencesService) : ModelStore<Operation>("operations", prefs) {

    init {
        load()
    }

    override fun create(jsonObject: JSONObject?): Operation {
        if (jsonObject == null) {
            throw NullPointerException("jsonObject")
        }

        if (!jsonObject.has(Operation::name.name)) {
            throw IllegalArgumentException("jsonObject must have '${Operation::name.name}' attribute")
        }

        // Determine the type of operation based on the name property in the json
        val operation = when (val operationName = jsonObject.getString(Operation::name.name)) {
            IdentityOperationExecutor.SET_ALIAS -> SetAliasOperation()
            IdentityOperationExecutor.DELETE_ALIAS -> DeleteAliasOperation()
            SubscriptionOperationExecutor.CREATE_SUBSCRIPTION -> CreateSubscriptionOperation()
            SubscriptionOperationExecutor.UPDATE_SUBSCRIPTION -> UpdateSubscriptionOperation()
            SubscriptionOperationExecutor.DELETE_SUBSCRIPTION -> DeleteSubscriptionOperation()
            UserOperationExecutor.CREATE_USER -> CreateUserOperation()
            UserOperationExecutor.SET_TAG -> SetTagOperation()
            UserOperationExecutor.DELETE_TAG -> DeleteTagOperation()
            UserOperationExecutor.SET_PROPERTY -> SetPropertyOperation()
            UserOperationExecutor.TRACK_SESSION -> TrackSessionOperation()
            UserOperationExecutor.TRACK_PURCHASE -> TrackPurchaseOperation()
            else -> throw Exception("Unrecognized operation: $operationName")
        }

        // populate the operation with the data.
        operation.initializeFromJson(jsonObject)

        return operation
    }
}
