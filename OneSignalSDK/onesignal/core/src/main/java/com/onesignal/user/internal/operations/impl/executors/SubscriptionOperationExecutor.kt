package com.onesignal.user.internal.operations.impl.executors

import android.os.Build
import com.onesignal.common.AndroidUtils
import com.onesignal.common.DeviceUtils
import com.onesignal.common.NetworkUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.RootToolsInternalMethods
import com.onesignal.common.consistency.IamFetchReadyCondition
import com.onesignal.common.consistency.enums.IamFetchRywTokenKey
import com.onesignal.common.consistency.models.IConsistencyManager
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.ISubscriptionBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.backend.SubscriptionObject
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.builduser.IRebuildUserService
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.CreateSubscriptionOperation
import com.onesignal.user.internal.operations.DeleteSubscriptionOperation
import com.onesignal.user.internal.operations.TransferSubscriptionOperation
import com.onesignal.user.internal.operations.UpdateSubscriptionOperation
import com.onesignal.user.internal.operations.impl.states.NewRecordsState
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionType

internal class SubscriptionOperationExecutor(
    private val _subscriptionBackend: ISubscriptionBackendService,
    private val _deviceService: IDeviceService,
    private val _applicationService: IApplicationService,
    private val _identityModelStore: IdentityModelStore,
    private val _subscriptionModelStore: SubscriptionModelStore,
    private val _configModelStore: ConfigModelStore,
    private val _buildUserService: IRebuildUserService,
    private val _newRecordState: NewRecordsState,
    private val _consistencyManager: IConsistencyManager,
) : IOperationExecutor {
    override val operations: List<String>
        get() = listOf(CREATE_SUBSCRIPTION, UPDATE_SUBSCRIPTION, DELETE_SUBSCRIPTION, TRANSFER_SUBSCRIPTION)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse {
        Logging.log(LogLevel.DEBUG, "SubscriptionOperationExecutor(operations: $operations)")

        val startingOp = operations.first()

        return if (startingOp is CreateSubscriptionOperation) {
            createSubscription(startingOp, operations)
        } else if (operations.any { it is DeleteSubscriptionOperation }) {
            if (operations.size > 1) {
                throw Exception("Only supports one operation! Attempted operations:\n$operations")
            }
            val deleteSubOps = operations.filterIsInstance<DeleteSubscriptionOperation>()
            deleteSubscription(deleteSubOps.first())
        } else if (startingOp is UpdateSubscriptionOperation) {
            updateSubscription(startingOp, operations)
        } else if (startingOp is TransferSubscriptionOperation) {
            if (operations.size > 1) {
                throw Exception("TransferSubscriptionOperation only supports one operation! Attempted operations:\n$operations")
            }
            transferSubscription(startingOp)
        } else {
            throw Exception("Unrecognized operation: $startingOp")
        }
    }

    private suspend fun createSubscription(
        createOperation: CreateSubscriptionOperation,
        operations: List<Operation>,
    ): ExecutionResponse {
        // if there are any deletes all operations should be tossed, nothing to do.
        if (operations.any { it is DeleteSubscriptionOperation }) {
            return ExecutionResponse(ExecutionResult.SUCCESS)
        }

        // the effective enabled/address for this subscription is the last update performed, if there is one. If there
        // isn't one we fall back to whatever is in the create operation.
        val lastUpdateOperation = operations.lastOrNull { it is UpdateSubscriptionOperation } as UpdateSubscriptionOperation?
        val enabled = lastUpdateOperation?.enabled ?: createOperation.enabled
        val address = lastUpdateOperation?.address ?: createOperation.address
        val status = lastUpdateOperation?.status ?: createOperation.status

        try {
            val subscription =
                SubscriptionObject(
                    id = null,
                    convert(createOperation.type),
                    address,
                    enabled,
                    status.value,
                    OneSignalUtils.SDK_VERSION,
                    Build.MODEL,
                    Build.VERSION.RELEASE,
                    RootToolsInternalMethods.isRooted,
                    DeviceUtils.getNetType(_applicationService.appContext),
                    DeviceUtils.getCarrierName(_applicationService.appContext),
                    AndroidUtils.getAppVersion(_applicationService.appContext),
                )

            val identityAlias = _identityModelStore.getIdentityAlias()
            val result =
                _subscriptionBackend.createSubscription(
                    createOperation.appId,
                    identityAlias.first,
                    identityAlias.second,
                    subscription,
                    _identityModelStore.model.jwtToken,
                ) ?: return ExecutionResponse(ExecutionResult.SUCCESS)

            val backendSubscriptionId = result.first
            val rywData = result.second

            if (rywData != null) {
                _consistencyManager.setRywData(createOperation.onesignalId, IamFetchRywTokenKey.SUBSCRIPTION, rywData)
            } else {
                _consistencyManager.resolveConditionsWithID(IamFetchReadyCondition.ID)
            }

            // update the subscription model with the new ID, if it's still active.
            val subscriptionModel = _subscriptionModelStore.get(createOperation.subscriptionId)
            subscriptionModel?.setStringProperty(
                SubscriptionModel::id.name,
                backendSubscriptionId,
                ModelChangeTags.HYDRATE,
            )

            if (_configModelStore.model.pushSubscriptionId == createOperation.subscriptionId) {
                _configModelStore.model.pushSubscriptionId = backendSubscriptionId
            }

            return ExecutionResponse(
                ExecutionResult.SUCCESS,
                mapOf(createOperation.subscriptionId to backendSubscriptionId),
            )
        } catch (ex: BackendException) {
            val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)

            return when (responseType) {
                NetworkUtils.ResponseStatusType.RETRYABLE ->
                    ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                NetworkUtils.ResponseStatusType.CONFLICT,
                NetworkUtils.ResponseStatusType.INVALID,
                ->
                    ExecutionResponse(ExecutionResult.FAIL_NORETRY)
                NetworkUtils.ResponseStatusType.UNAUTHORIZED -> {
                    _identityModelStore.invalidateJwt()
                    ExecutionResponse(ExecutionResult.FAIL_UNAUTHORIZED, retryAfterSeconds = ex.retryAfterSeconds)
                }
                NetworkUtils.ResponseStatusType.MISSING -> {
                    if (ex.statusCode == 404 && _newRecordState.isInMissingRetryWindow(createOperation.onesignalId)) {
                        return ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                    }
                    val operations = _buildUserService.getRebuildOperationsIfCurrentUser(createOperation.appId, createOperation.onesignalId)
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
            }
        }
    }

    private suspend fun updateSubscription(
        startingOperation: UpdateSubscriptionOperation,
        operations: List<Operation>,
    ): ExecutionResponse {
        // the effective enabled/address is the last update performed
        val lastOperation = operations.last() as UpdateSubscriptionOperation
        try {
            val subscription =
                SubscriptionObject(
                    id = null,
                    convert(lastOperation.type),
                    lastOperation.address,
                    lastOperation.enabled,
                    lastOperation.status.value,
                    OneSignalUtils.SDK_VERSION,
                    Build.MODEL,
                    Build.VERSION.RELEASE,
                    RootToolsInternalMethods.isRooted,
                    DeviceUtils.getNetType(_applicationService.appContext),
                    DeviceUtils.getCarrierName(_applicationService.appContext),
                    AndroidUtils.getAppVersion(_applicationService.appContext),
                )

            val rywData = _subscriptionBackend.updateSubscription(lastOperation.appId, lastOperation.subscriptionId, subscription)

            if (rywData != null) {
                _consistencyManager.setRywData(startingOperation.onesignalId, IamFetchRywTokenKey.SUBSCRIPTION, rywData)
            } else {
                _consistencyManager.resolveConditionsWithID(IamFetchReadyCondition.ID)
            }
        } catch (ex: BackendException) {
            val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)

            return when (responseType) {
                NetworkUtils.ResponseStatusType.RETRYABLE ->
                    ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                NetworkUtils.ResponseStatusType.MISSING -> {
                    if (ex.statusCode == 404 &&
                        listOf(
                            lastOperation.onesignalId,
                            lastOperation.subscriptionId,
                        ).any { _newRecordState.isInMissingRetryWindow(it) }
                    ) {
                        return ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                    }
                    // toss this, but create an identical CreateSubscriptionOperation to re-create the subscription being updated.
                    ExecutionResponse(
                        ExecutionResult.FAIL_NORETRY,
                        operations =
                            listOf(
                                CreateSubscriptionOperation(
                                    lastOperation.appId,
                                    lastOperation.onesignalId,
                                    lastOperation.subscriptionId,
                                    lastOperation.type,
                                    lastOperation.enabled,
                                    lastOperation.address,
                                    lastOperation.status,
                                ),
                            ),
                    )
                }
                else ->
                    ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }
        }

        return ExecutionResponse(ExecutionResult.SUCCESS)
    }

    // TODO: whenever the end-user changes users, we need to add the read-your-write token here, currently no code to handle the re-fetch IAMs
    private suspend fun transferSubscription(startingOperation: TransferSubscriptionOperation): ExecutionResponse {
        try {
            _subscriptionBackend.transferSubscription(
                startingOperation.appId,
                startingOperation.subscriptionId,
                IdentityConstants.ONESIGNAL_ID,
                startingOperation.onesignalId,
                _identityModelStore.model.jwtToken,
            )
        } catch (ex: BackendException) {
            val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)

            return when (responseType) {
                NetworkUtils.ResponseStatusType.RETRYABLE ->
                    ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                else ->
                    ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }
        }

        return ExecutionResponse(ExecutionResult.SUCCESS)
    }

    private fun convert(subscriptionType: SubscriptionType): SubscriptionObjectType {
        return when (subscriptionType) {
            SubscriptionType.SMS -> {
                SubscriptionObjectType.SMS
            }
            SubscriptionType.EMAIL -> {
                SubscriptionObjectType.EMAIL
            }
            else -> {
                SubscriptionObjectType.fromDeviceType(_deviceService.deviceType)
            }
        }
    }

    private suspend fun deleteSubscription(op: DeleteSubscriptionOperation): ExecutionResponse {
        try {
            _subscriptionBackend.deleteSubscription(op.appId, op.subscriptionId, _identityModelStore.model.jwtToken)

            // remove the subscription model as a HYDRATE in case for some reason it still exists.
            _subscriptionModelStore.remove(op.subscriptionId, ModelChangeTags.HYDRATE)
        } catch (ex: BackendException) {
            val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)

            return when (responseType) {
                NetworkUtils.ResponseStatusType.MISSING -> {
                    if (ex.statusCode == 404 &&
                        listOf(
                            op.onesignalId,
                            op.subscriptionId,
                        ).any { _newRecordState.isInMissingRetryWindow(it) }
                    ) {
                        ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                    } else {
                        // if the subscription is missing, we are good!
                        ExecutionResponse(ExecutionResult.SUCCESS)
                    }
                }
                NetworkUtils.ResponseStatusType.RETRYABLE ->
                    ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                else ->
                    ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }
        }

        return ExecutionResponse(ExecutionResult.SUCCESS)
    }

    companion object {
        const val CREATE_SUBSCRIPTION = "create-subscription"
        const val UPDATE_SUBSCRIPTION = "update-subscription"
        const val DELETE_SUBSCRIPTION = "delete-subscription"
        const val TRANSFER_SUBSCRIPTION = "transfer-subscription"
    }
}
