package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.LoginUserOperation

class LogoutHelper(
    private val logoutLock: Any,
    private val identityModelStore: IdentityModelStore,
    private val userSwitcher: UserSwitcher,
    private val operationRepo: IOperationRepo,
    private val configModel: ConfigModel
) {
    fun logout() {
        synchronized(logoutLock) {
            if (identityModelStore.model.externalId == null) {
                return
            }

            userSwitcher.createAndSwitchToNewUser()
            operationRepo.enqueue(
                LoginUserOperation(
                    configModel.appId,
                    identityModelStore.model.onesignalId,
                    identityModelStore.model.externalId,
                ),
            )

            // TODO: remove JWT Token for all future requests.
        }
    }
}
