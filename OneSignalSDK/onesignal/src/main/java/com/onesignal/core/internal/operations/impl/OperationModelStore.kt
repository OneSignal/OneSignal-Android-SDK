package com.onesignal.core.internal.operations.impl

import com.onesignal.common.modeling.ModelStore
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.user.internal.operations.CreateSubscriptionOperation
import com.onesignal.user.internal.operations.CreateUserOperation
import com.onesignal.user.internal.operations.DeleteAliasOperation
import com.onesignal.user.internal.operations.DeleteSubscriptionOperation
import com.onesignal.user.internal.operations.DeleteTagOperation
import com.onesignal.user.internal.operations.SetAliasOperation
import com.onesignal.user.internal.operations.SetPropertyOperation
import com.onesignal.user.internal.operations.SetTagOperation
import com.onesignal.user.internal.operations.TrackPurchaseOperation
import com.onesignal.user.internal.operations.TrackSessionOperation
import com.onesignal.user.internal.operations.UpdateSubscriptionOperation
import com.onesignal.user.internal.operations.impl.executors.IdentityOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.SubscriptionOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.UserOperationExecutor
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
