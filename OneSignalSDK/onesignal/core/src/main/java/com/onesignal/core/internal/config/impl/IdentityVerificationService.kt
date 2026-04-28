package com.onesignal.core.internal.config.impl

import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.user.internal.jwt.JwtRequirement

/**
 * Forwards customer-side IV state (`jwt_required`) from config HYDRATE events to
 * [IOperationRepo.onJwtConfigHydrated], which owns the post-HYDRATE queue maintenance.
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
        _operationRepo.onJwtConfigHydrated(model.useIdentityVerification == JwtRequirement.REQUIRED)
    }

    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        // Remote params arrive as full-model replacements (HYDRATE); individual property
        // updates are not expected for useIdentityVerification.
    }
}
