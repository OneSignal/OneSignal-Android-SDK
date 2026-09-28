package com.onesignal.core.internal.operations.impl

import com.onesignal.common.modeling.ModelStore
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.PreferenceOneSignalKeys
import com.onesignal.core.internal.preferences.PreferenceStores
import com.onesignal.core.internal.time.ITime
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
import com.onesignal.user.internal.operations.TrackCustomEventOperation
import com.onesignal.user.internal.operations.TrackPurchaseOperation
import com.onesignal.user.internal.operations.TrackSessionEndOperation
import com.onesignal.user.internal.operations.TrackSessionStartOperation
import com.onesignal.user.internal.operations.TransferSubscriptionOperation
import com.onesignal.user.internal.operations.UpdateSubscriptionOperation
import com.onesignal.user.internal.operations.impl.executors.CustomEventOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.IdentityOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.LoginUserFromSubscriptionOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.LoginUserOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.RefreshUserOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.SubscriptionOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.UpdateUserOperationExecutor
import org.json.JSONObject

internal class OperationModelStore(
    private val prefs: IPreferencesService,
    private val time: ITime,
) : ModelStore<Operation>(STORE_NAME, prefs) {
    fun loadOperations() {
        discardOversizedStore()
        load()
    }

    /**
     * Resets a persisted array over [MAX_PERSISTED_LENGTH] instead of parsing it. The queue cap keeps a
     * store written by this SDK far below it.
     */
    private fun discardOversizedStore() {
        val key = PreferenceOneSignalKeys.MODEL_STORE_PREFIX + STORE_NAME
        val length = prefs.getString(PreferenceStores.ONESIGNAL, key, "[]")?.length ?: 0
        if (length <= MAX_PERSISTED_LENGTH) {
            return
        }
        Logging.error("OperationModelStore: discarding a persisted operation queue of $length characters, over the $MAX_PERSISTED_LENGTH limit")
        prefs.saveString(PreferenceStores.ONESIGNAL, key, "[]")
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
                CustomEventOperationExecutor.CUSTOM_EVENT -> TrackCustomEventOperation()
                else -> throw Exception("Unrecognized operation: $operationName")
            }

        // populate the operation with the data.
        operation.initializeFromJson(jsonObject)

        // Persisted before createdAt existed, so its clock starts now.
        if (operation.createdAt == null) {
            operation.createdAt = time.currentTimeMillis
        }

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

    companion object {
        const val STORE_NAME = "operations"
        const val MAX_PERSISTED_LENGTH = 1_048_576
    }
}
