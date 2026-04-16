package com.onesignal.core.internal.config.impl

import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.UserManager
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.identity.JwtTokenStore

/**
 * Reacts to the identity-verification remote param arriving via config HYDRATE.
 *
 * - When IV transitions from unknown (null) to true: purges anonymous operations.
 * - When IV transitions from unknown (null) to any value: wakes the operation queue.
 * - On beta migration: if IV=true and the current user has an externalId but no JWT,
 *   fires [UserJwtInvalidatedEvent] so the developer provides a fresh token.
 */
internal class IdentityVerificationService(
    private val _configModelStore: ConfigModelStore,
    private val _operationRepo: IOperationRepo,
    private val _identityModelStore: IdentityModelStore,
    private val _jwtTokenStore: JwtTokenStore,
    private val _userManager: UserManager,
) : IStartableService, ISingletonModelStoreChangeHandler<ConfigModel> {
    override fun start() {
        _configModelStore.subscribe(this)
        _operationRepo.setJwtInvalidatedHandler { externalId ->
            _userManager.fireJwtInvalidated(externalId)
        }
    }

    override fun onModelReplaced(
        model: ConfigModel,
        tag: String,
    ) {
        if (tag != ModelChangeTags.HYDRATE) return

        val useIV = model.useIdentityVerification

        var jwtInvalidatedExternalId: String? = null
        if (useIV == true) {
            Logging.debug("IdentityVerificationService: IV enabled, purging anonymous operations")
            _operationRepo.removeOperationsWithoutExternalId()

            val externalId = _identityModelStore.model.externalId
            if (externalId != null && _jwtTokenStore.getJwt(externalId) == null) {
                Logging.debug("IdentityVerificationService: IV enabled but no JWT for $externalId, will fire invalidated event after queue wake")
                jwtInvalidatedExternalId = externalId
            }
        }

        _operationRepo.forceExecuteOperations()

        jwtInvalidatedExternalId?.let { _userManager.fireJwtInvalidated(it) }
    }

    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        // Individual property updates are not expected for remote params;
        // ConfigModelStoreListener replaces the entire model on HYDRATE.
    }
}
