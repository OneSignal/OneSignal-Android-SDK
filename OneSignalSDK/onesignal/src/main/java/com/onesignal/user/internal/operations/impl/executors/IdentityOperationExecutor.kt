package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.common.IDManager
import com.onesignal.common.exceptions.BackendException
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.IIdentityBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.operations.DeleteAliasOperation
import com.onesignal.user.internal.operations.SetAliasOperation

internal class IdentityOperationExecutor(
    private val _identityBackend: IIdentityBackendService
) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(SET_ALIAS, DELETE_ALIAS)

    override suspend fun execute(operations: List<Operation>) {
        Logging.debug("AliasOperationExecutor(operations: $operations)")

        // An alias group is an appId/onesignalId/aliasLabel combo, so we only care
        // about the last operation in the group, as that will be the effective end
        // state to this specific alias for this user.
        val lastOperation = operations.last()

        if (lastOperation is SetAliasOperation) {
            try {
                _identityBackend.createAlias(
                    lastOperation.appId,
                    IdentityConstants.ONESIGNAL_ID,
                    IDManager.retrieveId(lastOperation.onesignalId),
                    mapOf(lastOperation.label to lastOperation.value)
                )
            } catch (ex: BackendException) {
                // TODO: What to do on error
            }
        } else if (lastOperation is DeleteAliasOperation) {
            try {
                _identityBackend.deleteAlias(lastOperation.appId, IdentityConstants.ONESIGNAL_ID, IDManager.retrieveId(lastOperation.onesignalId), lastOperation.label)
            } catch (ex: BackendException) {
                // TODO: What to do on error
            }
        }
    }

    companion object {
        const val SET_ALIAS = "set-alias"
        const val DELETE_ALIAS = "delete-alias"
    }
}
