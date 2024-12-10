package com.onesignal.core.internal.operations.impl

import com.onesignal.common.modeling.ModelStore
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.operations.CreateSubscriptionOperation
import com.onesignal.user.internal.operations.DeleteAliasOperation
import com.onesignal.user.internal.operations.DeleteSubscriptionOperation
import com.onesignal.user.internal.operations.DeleteTagOperation
import com.onesignal.user.internal.operations.LoginUserFromSubscriptionOperation
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.operations.RefreshUserOperation
import com.onesignal.user.internal.operations.SetAliasOperation
import com.onesignal.user.internal.operations.SetPropertyOperation
import com.onesignal.user.internal.operations.SetTagOperation
import com.onesignal.user.internal.operations.TrackPurchaseOperation
import com.onesignal.user.internal.operations.TrackSessionEndOperation
import com.onesignal.user.internal.operations.TrackSessionStartOperation
import com.onesignal.user.internal.operations.TransferSubscriptionOperation
import com.onesignal.user.internal.operations.UpdateSubscriptionOperation
import com.onesignal.user.internal.operations.impl.executors.IdentityOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.LoginUserFromSubscriptionOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.LoginUserOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.RefreshUserOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.SubscriptionOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.UpdateUserOperationExecutor
import org.json.JSONObject

internal class OperationModelStore(prefs: IPreferencesService) : ModelStore<Operation>("operations", prefs) {
    fun loadOperations() {
        load()
        Logging.debug("OperationModelStore finished loading.")
    }

    override fun create(jsonObject: JSONObject?): Operation? {
        if (jsonObject == null) {
            Logging.error("null jsonObject sent to OperationModelStore.create")
            return null
        }

        if (!isValidOperation(jsonObject)) {
            return null
        }

        // Determine the type of operation based on the name property in the json
        val operation =
            when (val operationName = jsonObject.getString(Operation::name.name)) {
                IdentityOperationExecutor.SET_ALIAS -> SetAliasOperation()
                IdentityOperationExecutor.DELETE_ALIAS -> DeleteAliasOperation()
                SubscriptionOperationExecutor.CREATE_SUBSCRIPTION -> CreateSubscriptionOperation()
                SubscriptionOperationExecutor.UPDATE_SUBSCRIPTION -> UpdateSubscriptionOperation()
                SubscriptionOperationExecutor.DELETE_SUBSCRIPTION -> DeleteSubscriptionOperation()
                SubscriptionOperationExecutor.TRANSFER_SUBSCRIPTION -> TransferSubscriptionOperation()
                LoginUserOperationExecutor.LOGIN_USER -> LoginUserOperation()
                LoginUserFromSubscriptionOperationExecutor.LOGIN_USER_FROM_SUBSCRIPTION_USER -> LoginUserFromSubscriptionOperation()
                RefreshUserOperationExecutor.REFRESH_USER -> RefreshUserOperation()
                UpdateUserOperationExecutor.SET_TAG -> SetTagOperation()
                UpdateUserOperationExecutor.DELETE_TAG -> DeleteTagOperation()
                UpdateUserOperationExecutor.SET_PROPERTY -> SetPropertyOperation()
                UpdateUserOperationExecutor.TRACK_SESSION_START -> TrackSessionStartOperation()
                UpdateUserOperationExecutor.TRACK_SESSION_END -> TrackSessionEndOperation()
                UpdateUserOperationExecutor.TRACK_PURCHASE -> TrackPurchaseOperation()
                else -> throw Exception("Unrecognized operation: $operationName")
            }

        Thread.sleep(5000)
        // populate the operation with the data.
        operation.initializeFromJson(jsonObject)

        return operation
    }

    /**
     * Checks if a JSONObject is a valid Operation. Contains a check for onesignalId.
     * This is a rare case that a cached Operation is missing the onesignalId,
     * which would continuously cause crashes when the Operation is processed.
     *
     * @param jsonObject The [JSONObject] that represents an Operation
     */
    private fun isValidOperation(jsonObject: JSONObject): Boolean {
        if (!jsonObject.has(Operation::name.name)) {
            Logging.error("jsonObject must have '${Operation::name.name}' attribute")
            return false
        }

        val operationName = jsonObject.getString(Operation::name.name)

        val excluded =
            setOf(
                LoginUserOperationExecutor.LOGIN_USER,
                LoginUserFromSubscriptionOperationExecutor.LOGIN_USER_FROM_SUBSCRIPTION_USER,
            )

        // Must have onesignalId if it is not one of the excluded operations above
        if (!jsonObject.has("onesignalId") && !excluded.contains(operationName)) {
            Logging.error("$operationName jsonObject must have 'onesignalId' attribute")
            return false
        }

        return true
    }
}
