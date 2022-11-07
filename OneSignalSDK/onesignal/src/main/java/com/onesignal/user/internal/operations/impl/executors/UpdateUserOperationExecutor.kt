package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.common.NetworkUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.backend.PropertiesDeltasObject
import com.onesignal.user.internal.backend.PropertiesObject
import com.onesignal.user.internal.backend.PurchaseObject
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.DeleteTagOperation
import com.onesignal.user.internal.operations.SetPropertyOperation
import com.onesignal.user.internal.operations.SetTagOperation
import com.onesignal.user.internal.operations.TrackPurchaseOperation
import com.onesignal.user.internal.operations.TrackSessionOperation
import com.onesignal.user.internal.properties.PropertiesModelStore

internal class UpdateUserOperationExecutor(
    private val _userBackend: IUserBackendService,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore
) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(SET_TAG, DELETE_TAG, SET_PROPERTY, TRACK_SESSION, TRACK_PURCHASE)

    override suspend fun execute(ops: List<Operation>): ExecutionResponse {
        Logging.log(LogLevel.DEBUG, "UserOperationExecutor(operation: $operations)")

        var appId: String? = null
        var onesignalId: String? = null

        var propertiesObject = PropertiesObject()
        var deltasObject = PropertiesDeltasObject()

        for (operation in ops) {
            when (operation) {
                is SetTagOperation -> {
                    if (appId == null) {
                        appId = operation.appId
                        onesignalId = operation.onesignalId
                    }

                    propertiesObject = PropertyOperationHelper.createPropertiesFromOperation(operation, propertiesObject)
                }
                is DeleteTagOperation -> {
                    if (appId == null) {
                        appId = operation.appId
                        onesignalId = operation.onesignalId
                    }

                    propertiesObject = PropertyOperationHelper.createPropertiesFromOperation(operation, propertiesObject)
                }
                is SetPropertyOperation -> {
                    if (appId == null) {
                        appId = operation.appId
                        onesignalId = operation.onesignalId
                    }

                    propertiesObject = PropertyOperationHelper.createPropertiesFromOperation(operation, propertiesObject)
                }
                is TrackSessionOperation -> {
                    if (appId == null) {
                        appId = operation.appId
                        onesignalId = operation.onesignalId
                    }

                    // The session count we pass up is essentially the number of `TrackSessionOperation`
                    // operations we come across in this group, while the session time we pass up is
                    // the total session time across all `TrackSessionOperation` operations
                    // that exist in this group.
                    val sessionTime = if (deltasObject.sessionTime != null) deltasObject.sessionTime!! + operation.sessionTime else operation.sessionTime
                    val sessionCount = if (deltasObject.sessionCounts != null) deltasObject.sessionCounts!! + 1 else 1

                    deltasObject = PropertiesDeltasObject(sessionTime, sessionCount, deltasObject.amountSpent, deltasObject.purchases)
                }
                is TrackPurchaseOperation -> {
                    if (appId == null) {
                        appId = operation.appId
                        onesignalId = operation.onesignalId
                    }

                    // The amount spent we pass up is the total amount spent across all `TrackPurchaseOperation`
                    // operations that exist in this group, while the purchases is the union of all
                    // `TrackPurchaseOperation` operations that exist in this group.
                    val amountSpent = if (deltasObject.amountSpent != null) deltasObject.amountSpent!! + operation.amountSpent else operation.amountSpent
                    val purchasesArray = if (deltasObject.purchases != null) deltasObject.purchases!!.toMutableList() else mutableListOf()

                    for (purchase in operation.purchases) {
                        purchasesArray.add(PurchaseObject(purchase.sku, purchase.iso, purchase.amount))
                    }

                    deltasObject = PropertiesDeltasObject(deltasObject.sessionTime, deltasObject.sessionCounts, amountSpent, purchasesArray)
                }
            }
        }

        if (appId != null && onesignalId != null) {
            try {
                _userBackend.updateUser(appId, IdentityConstants.ONESIGNAL_ID, onesignalId, propertiesObject, true, deltasObject)

                if (_identityModelStore.model.onesignalId == onesignalId) {
                    // go through and make sure any properties are in the correct model state
                    for (operation in ops) {
                        when (operation) {
                            is SetTagOperation -> _propertiesModelStore.model.tags.setProperty(operation.key, operation.value, ModelChangeTags.HYDRATE)
                            is DeleteTagOperation -> _propertiesModelStore.model.tags.setProperty(operation.key, null, ModelChangeTags.HYDRATE)
                            is SetPropertyOperation -> _propertiesModelStore.model.setProperty(operation.property, operation.value, ModelChangeTags.HYDRATE)
                        }
                    }
                }
            } catch (ex: BackendException) {
                return if (NetworkUtils.shouldRetryNetworkRequest(ex.statusCode)) {
                    ExecutionResponse(ExecutionResult.FAIL_RETRY)
                } else {
                    ExecutionResponse(ExecutionResult.FAIL_NORETRY)
                }
            }
        }

        return ExecutionResponse(ExecutionResult.SUCCESS)
    }

    companion object {
        const val SET_TAG = "set-tag"
        const val DELETE_TAG = "delete-tag"
        const val SET_PROPERTY = "set-property"
        const val TRACK_SESSION = "track-session"
        const val TRACK_PURCHASE = "track-purchase"
    }
}
