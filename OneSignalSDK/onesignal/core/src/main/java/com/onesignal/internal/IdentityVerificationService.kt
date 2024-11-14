package com.onesignal.internal

import com.onesignal.core.internal.backend.ParamsObject
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.config.FetchParamsObserver
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.startup.IBootstrapService
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.LoginUserOperation

internal class IdentityVerificationService(
    private val _configModelStore: ConfigModelStore,
    private val _identityModelStore: IdentityModelStore,
    private val opRepo: IOperationRepo,
) : IBootstrapService {
    override fun bootstrap() {
        _configModelStore.model.addFetchParamsObserver(
            object : FetchParamsObserver {
                override fun onParamsFetched(params: ParamsObject) {
                    if (params.useIdentityVerification == true && _identityModelStore.model.jwtToken == null) {
                        Logging.log(LogLevel.INFO, "A valid JWT is required for user ${_identityModelStore.model.externalId}.")
                        return
                    }

                    // calling login either identity verification is turned off or a jwt is cached
                    opRepo.enqueue(
                        LoginUserOperation(
                            _configModelStore.model.appId,
                            _identityModelStore.model.onesignalId,
                            _identityModelStore.model.externalId,
                        ),
                    )
                }
            },
        )
    }
}
