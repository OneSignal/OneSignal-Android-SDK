package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.identity.JwtTokenStore
import com.onesignal.user.internal.operations.LoginUserOperation

class LoginHelper(
    private val identityModelStore: IdentityModelStore,
    private val userSwitcher: UserSwitcher,
    private val operationRepo: IOperationRepo,
    private val configModel: ConfigModel,
    private val jwtTokenStore: JwtTokenStore,
    private val lock: Any,
) {
    suspend fun login(
        externalId: String,
        jwtBearerToken: String? = null,
    ) {
        var currentIdentityExternalId: String? = null
        var currentIdentityOneSignalId: String? = null
        var newIdentityOneSignalId: String = ""

        synchronized(lock) {
            currentIdentityExternalId = identityModelStore.model.externalId
            currentIdentityOneSignalId = identityModelStore.model.onesignalId

            if (currentIdentityExternalId == externalId) {
                jwtTokenStore.putJwt(externalId, jwtBearerToken)
                operationRepo.forceExecuteOperations()
                return
            }

            jwtTokenStore.putJwt(externalId, jwtBearerToken)

            userSwitcher.createAndSwitchToNewUser { identityModel, _ ->
                identityModel.externalId = externalId
            }

            newIdentityOneSignalId = identityModelStore.model.onesignalId
        }

        val existingOneSignalId =
            if (configModel.useIdentityVerification == true) {
                null
            } else {
                if (currentIdentityExternalId == null) currentIdentityOneSignalId else null
            }

        val result =
            operationRepo.enqueueAndWait(
                LoginUserOperation(
                    configModel.appId,
                    newIdentityOneSignalId,
                    externalId,
                    existingOneSignalId,
                ),
            )

        if (!result) {
            Logging.warn("Could not login user")
        }
    }
}
