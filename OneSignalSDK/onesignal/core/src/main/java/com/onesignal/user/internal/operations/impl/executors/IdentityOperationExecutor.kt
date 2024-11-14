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
import com.onesignal.user.internal.builduser.IRebuildUserService
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.DeleteAliasOperation
import com.onesignal.user.internal.operations.SetAliasOperation
import com.onesignal.user.internal.operations.impl.states.NewRecordsState

internal class IdentityOperationExecutor(
    private val _identityBackend: IIdentityBackendService,
    private val _identityModelStore: IdentityModelStore,
    private val _buildUserService: IRebuildUserService,
    private val _newRecordState: NewRecordsState,
) : IOperationExecutor {
    override val operations: List<String>
        get() = listOf(SET_ALIAS, DELETE_ALIAS)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse {
        Logging.debug("IdentityOperationExecutor(operations: $operations)")

        if (operations.any { it !is SetAliasOperation && it !is DeleteAliasOperation }) {
            throw Exception("Unrecognized operation(s)! Attempted operations:\n$operations")
        }

        if (operations.any { it is SetAliasOperation } &&
            operations.any { it is DeleteAliasOperation }
        ) {
            throw Exception("Can't process SetAliasOperation and DeleteAliasOperation at the same time.")
        }

        // An alias group is an appId/onesignalId/aliasLabel combo, so we only care
        // about the last operation in the group, as that will be the effective end
        // state to this specific alias for this user.
        val lastOperation = operations.last()

        if (lastOperation is SetAliasOperation) {
            try {
                val identityAlias = _identityModelStore.getIdentityAlias()
                _identityBackend.setAlias(
                    lastOperation.appId,
                    identityAlias.first,
                    identityAlias.second,
                    mapOf(lastOperation.label to lastOperation.value),
                    _identityModelStore.model.jwtToken,
                )

                // ensure the now created alias is in the model as long as the user is still current.
                if (_identityModelStore.model.onesignalId == lastOperation.onesignalId) {
                    _identityModelStore.model.setStringProperty(lastOperation.label, lastOperation.value, ModelChangeTags.HYDRATE)
                }
            } catch (ex: BackendException) {
                val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)

                return when (responseType) {
                    NetworkUtils.ResponseStatusType.RETRYABLE ->
                        ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                    NetworkUtils.ResponseStatusType.INVALID ->
                        ExecutionResponse(ExecutionResult.FAIL_NORETRY)
                    NetworkUtils.ResponseStatusType.CONFLICT ->
                        ExecutionResponse(ExecutionResult.FAIL_CONFLICT, retryAfterSeconds = ex.retryAfterSeconds)
                    NetworkUtils.ResponseStatusType.UNAUTHORIZED -> {
                        return ExecutionResponse(ExecutionResult.FAIL_UNAUTHORIZED)
                    }
                    NetworkUtils.ResponseStatusType.MISSING -> {
                        if (ex.statusCode == 404 && _newRecordState.isInMissingRetryWindow(lastOperation.onesignalId)) {
                            return ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                        }

                        val rebuildOps = _buildUserService.getRebuildOperationsIfCurrentUser(lastOperation.appId, lastOperation.onesignalId)
                        if (rebuildOps == null) {
                            return ExecutionResponse(ExecutionResult.FAIL_NORETRY)
                        } else {
                            return ExecutionResponse(
                                ExecutionResult.FAIL_RETRY,
                                operations = rebuildOps,
                                retryAfterSeconds = ex.retryAfterSeconds,
                            )
                        }
                    }
                }
            }
        } else if (lastOperation is DeleteAliasOperation) {
            try {
                _identityBackend.deleteAlias(
                    lastOperation.appId,
                    IdentityConstants.ONESIGNAL_ID,
                    lastOperation.onesignalId,
                    lastOperation.label,
                    _identityModelStore.model.jwtToken,
                )

                // ensure the now deleted alias is not in the model as long as the user is still current.
                if (_identityModelStore.model.onesignalId == lastOperation.onesignalId) {
                    _identityModelStore.model.setOptStringProperty(lastOperation.label, null, ModelChangeTags.HYDRATE)
                }
            } catch (ex: BackendException) {
                val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)

                return when (responseType) {
                    NetworkUtils.ResponseStatusType.RETRYABLE ->
                        ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                    NetworkUtils.ResponseStatusType.CONFLICT ->
                        // A conflict indicates the alias doesn't exist on the user it's being deleted from. This is good!
                        ExecutionResponse(ExecutionResult.SUCCESS)
                    NetworkUtils.ResponseStatusType.INVALID ->
                        ExecutionResponse(ExecutionResult.FAIL_NORETRY)
                    NetworkUtils.ResponseStatusType.UNAUTHORIZED -> {
                        return ExecutionResponse(ExecutionResult.FAIL_UNAUTHORIZED)
                    }
                    NetworkUtils.ResponseStatusType.MISSING -> {
                        return if (ex.statusCode == 404 && _newRecordState.isInMissingRetryWindow(lastOperation.onesignalId)) {
                            ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                        } else {
                            // This means either the User or the Alias was already
                            // deleted, either way the end state is the same, the
                            // alias no longer exists on that User.
                            ExecutionResponse(ExecutionResult.SUCCESS)
                        }
                    }
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
