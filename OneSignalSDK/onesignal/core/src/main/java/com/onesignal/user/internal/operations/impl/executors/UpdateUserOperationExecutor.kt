package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.common.NetworkUtils
import com.onesignal.common.consistency.IamFetchReadyCondition
import com.onesignal.common.consistency.enums.IamFetchRywTokenKey
import com.onesignal.common.consistency.models.IConsistencyManager
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
import com.onesignal.user.internal.builduser.IRebuildUserService
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.DeleteTagOperation
import com.onesignal.user.internal.operations.SetPropertyOperation
import com.onesignal.user.internal.operations.SetTagOperation
import com.onesignal.user.internal.operations.TrackPurchaseOperation
import com.onesignal.user.internal.operations.TrackSessionEndOperation
import com.onesignal.user.internal.operations.TrackSessionStartOperation
import com.onesignal.user.internal.operations.impl.states.NewRecordsState
import com.onesignal.user.internal.properties.PropertiesModelStore

internal class UpdateUserOperationExecutor(
    private val _userBackend: IUserBackendService,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _buildUserService: IRebuildUserService,
    private val _newRecordState: NewRecordsState,
    private val _consistencyManager: IConsistencyManager,
) : IOperationExecutor {
    override val operations: List<String>
        get() = listOf(SET_TAG, DELETE_TAG, SET_PROPERTY, TRACK_SESSION_START, TRACK_SESSION_END, TRACK_PURCHASE)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse {
        Logging.log(LogLevel.DEBUG, "UpdateUserOperationExecutor(operation: $operations)")

        var appId: String? = null
        var onesignalId: String? = null

        var propertiesObject = PropertiesObject()
        var deltasObject = PropertiesDeltasObject()
        var refreshDeviceMetadata = false

        for (operation in operations) {
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
                is TrackSessionStartOperation -> {
                    if (appId == null) {
                        appId = operation.appId
                        onesignalId = operation.onesignalId
                    }

                    // The session count we pass up is essentially the number of `TrackSessionStartOperation`
                    // operations we come across in this group, while the session time we pass up is
                    // the total session time across all `TrackSessionOperation` operations
                    // that exist in this group.
                    val sessionCount = if (deltasObject.sessionCount != null) deltasObject.sessionCount!! + 1 else 1

                    deltasObject =
                        PropertiesDeltasObject(deltasObject.sessionTime, sessionCount, deltasObject.amountSpent, deltasObject.purchases)
                    refreshDeviceMetadata = true
                }
                is TrackSessionEndOperation -> {
                    if (appId == null) {
                        appId = operation.appId
                        onesignalId = operation.onesignalId
                    }

                    // The session time we pass up is the total session time across all `TrackSessionEndOperation`
                    // operations that exist in this group.
                    val sessionTime =
                        if (deltasObject.sessionTime != null) {
                            deltasObject.sessionTime!! + operation.sessionTime
                        } else {
                            operation.sessionTime
                        }

                    deltasObject =
                        PropertiesDeltasObject(sessionTime, deltasObject.sessionCount, deltasObject.amountSpent, deltasObject.purchases)
                }
                is TrackPurchaseOperation -> {
                    if (appId == null) {
                        appId = operation.appId
                        onesignalId = operation.onesignalId
                    }

                    // The amount spent we pass up is the total amount spent across all `TrackPurchaseOperation`
                    // operations that exist in this group, while the purchases is the union of all
                    // `TrackPurchaseOperation` operations that exist in this group.
                    val amountSpent =
                        if (deltasObject.amountSpent != null) {
                            deltasObject.amountSpent!! + operation.amountSpent
                        } else {
                            operation.amountSpent
                        }
                    val purchasesArray = if (deltasObject.purchases != null) deltasObject.purchases!!.toMutableList() else mutableListOf()

                    for (purchase in operation.purchases) {
                        purchasesArray.add(PurchaseObject(purchase.sku, purchase.iso, purchase.amount))
                    }

                    deltasObject = PropertiesDeltasObject(deltasObject.sessionTime, deltasObject.sessionCount, amountSpent, purchasesArray)
                }
                else -> throw Exception("Unrecognized operation: $operation")
            }
        }

        if (appId != null && onesignalId != null) {
            try {
                val rywToken =
                    _userBackend.updateUser(
                        appId,
                        IdentityConstants.ONESIGNAL_ID,
                        onesignalId,
                        propertiesObject,
                        refreshDeviceMetadata,
                        deltasObject,
                    )

                if (rywToken != null) {
                    _consistencyManager.setRywToken(onesignalId, IamFetchRywTokenKey.USER, rywToken)
                } else {
                    _consistencyManager.resolveConditionsWithID(IamFetchReadyCondition.ID)
                }

                if (_identityModelStore.model.onesignalId == onesignalId) {
                    // go through and make sure any properties are in the correct model state
                    for (operation in operations) {
                        when (operation) {
                            is SetTagOperation ->
                                _propertiesModelStore.model.tags.setStringProperty(
                                    operation.key,
                                    operation.value,
                                    ModelChangeTags.HYDRATE,
                                )
                            is DeleteTagOperation ->
                                _propertiesModelStore.model.tags.setOptStringProperty(
                                    operation.key,
                                    null,
                                    ModelChangeTags.HYDRATE,
                                )
                            is SetPropertyOperation ->
                                _propertiesModelStore.model.setOptAnyProperty(
                                    operation.property,
                                    operation.value,
                                    ModelChangeTags.HYDRATE,
                                )
                        }
                    }
                }
            } catch (ex: BackendException) {
                val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)

                return when (responseType) {
                    NetworkUtils.ResponseStatusType.RETRYABLE ->
                        ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                    NetworkUtils.ResponseStatusType.UNAUTHORIZED ->
                        ExecutionResponse(ExecutionResult.FAIL_UNAUTHORIZED, retryAfterSeconds = ex.retryAfterSeconds)
                    NetworkUtils.ResponseStatusType.MISSING -> {
                        if (ex.statusCode == 404 && _newRecordState.isInMissingRetryWindow(onesignalId)) {
                            return ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                        }
                        val operations = _buildUserService.getRebuildOperationsIfCurrentUser(appId, onesignalId)
                        if (operations == null) {
                            return ExecutionResponse(ExecutionResult.FAIL_NORETRY)
                        } else {
                            return ExecutionResponse(
                                ExecutionResult.FAIL_RETRY,
                                operations = operations,
                                retryAfterSeconds = ex.retryAfterSeconds,
                            )
                        }
                    }
                    else ->
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
        const val TRACK_SESSION_START = "track-session-start"
        const val TRACK_SESSION_END = "track-session-end"
        const val TRACK_PURCHASE = "track-purchase"
    }
}
