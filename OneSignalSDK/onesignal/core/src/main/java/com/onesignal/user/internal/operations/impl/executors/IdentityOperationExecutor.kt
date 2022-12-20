package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.common.NetworkUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.IIdentityBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.DeleteAliasOperation
import com.onesignal.user.internal.operations.SetAliasOperation

internal class IdentityOperationExecutor(
    private val _identityBackend: IIdentityBackendService,
    private val _identityModelStore: IdentityModelStore
) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(SET_ALIAS, DELETE_ALIAS)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse {
        Logging.debug("IdentityOperationExecutor(operations: $operations)")

        // An alias group is an appId/onesignalId/aliasLabel combo, so we only care
        // about the last operation in the group, as that will be the effective end
        // state to this specific alias for this user.
        val lastOperation = operations.last()

        if (lastOperation is SetAliasOperation) {
            try {
                _identityBackend.setAlias(
                    lastOperation.appId,
                    IdentityConstants.ONESIGNAL_ID,
                    lastOperation.onesignalId,
                    mapOf(lastOperation.label to lastOperation.value)
                )

                // ensure the now created alias is in the model as long as the user is still current.
                if (_identityModelStore.model.onesignalId == lastOperation.onesignalId) {
                    _identityModelStore.model.setStringProperty(lastOperation.label, lastOperation.value, ModelChangeTags.HYDRATE)
                }
            } catch (ex: BackendException) {
                return if (ex.statusCode == 409) {
                    ExecutionResponse(ExecutionResult.FAIL_NORETRY)
                } else if (NetworkUtils.shouldRetryNetworkRequest(ex.statusCode)) {
                    ExecutionResponse(ExecutionResult.FAIL_RETRY)
                } else {
                    ExecutionResponse(ExecutionResult.FAIL_NORETRY)
                }
            }
        } else if (lastOperation is DeleteAliasOperation) {
            try {
                _identityBackend.deleteAlias(lastOperation.appId, IdentityConstants.ONESIGNAL_ID, lastOperation.onesignalId, lastOperation.label)

                // ensure the now deleted alias is not in the model as long as the user is still current.
                if (_identityModelStore.model.onesignalId == lastOperation.onesignalId) {
                    _identityModelStore.model.setOptStringProperty(lastOperation.label, null, ModelChangeTags.HYDRATE)
                }
            } catch (ex: BackendException) {
                return if (ex.statusCode == 409) {
                    // The 409 indicates the alias doesn't exist on the user it's being deleted from. This is good!
                    ExecutionResponse(ExecutionResult.SUCCESS)
                } else if (NetworkUtils.shouldRetryNetworkRequest(ex.statusCode)) {
                    ExecutionResponse(ExecutionResult.FAIL_RETRY)
                } else {
                    ExecutionResponse(ExecutionResult.FAIL_NORETRY)
                }
            }
        }

        return ExecutionResponse(ExecutionResult.SUCCESS)
    }

    companion object {
        const val SET_ALIAS = "set-alias"
        const val DELETE_ALIAS = "delete-alias"
    }
}
