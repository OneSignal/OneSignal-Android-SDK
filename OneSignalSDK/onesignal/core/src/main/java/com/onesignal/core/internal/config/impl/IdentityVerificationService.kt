package com.onesignal.core.internal.config.impl

import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.user.internal.jwt.JwtRequirement

/**
 * Reacts to the customer-side IV state (`jwt_required`) arriving via config HYDRATE.
 *
 * - If IV becomes [JwtRequirement.REQUIRED], purges anonymous ops — they can't execute without
 *   a JWT and would otherwise block the queue indefinitely.
 * - Always wakes the op queue after HYDRATE to release the pre-HYDRATE deferral in
 *   [com.onesignal.core.internal.operations.impl.OperationRepo.getNextOps].
 *
 * Purge is scheduled via `suspendifyOnIO` + `awaitInitialized` so it runs *after*
 * `loadSavedOperations` populates the queue (fix for an earlier race where purge ran
 * against an empty in-memory queue on cold start).
 */
internal class IdentityVerificationService(
    private val _configModelStore: ConfigModelStore,
    private val _operationRepo: IOperationRepo,
) : IStartableService, ISingletonModelStoreChangeHandler<ConfigModel> {
    override fun start() {
        _configModelStore.subscribe(this)
    }

    override fun onModelReplaced(
        model: ConfigModel,
        tag: String,
    ) {
        if (tag != ModelChangeTags.HYDRATE) return

        val requirement = model.useIdentityVerification
        suspendifyOnIO {
            _operationRepo.awaitInitialized()
            if (requirement == JwtRequirement.REQUIRED) {
                _operationRepo.removeOperationsWithoutExternalId()
            }
            _operationRepo.forceExecuteOperations()
        }
    }

    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        // Remote params arrive as full-model replacements (HYDRATE); individual property
        // updates are not expected for useIdentityVerification.
    }
}
