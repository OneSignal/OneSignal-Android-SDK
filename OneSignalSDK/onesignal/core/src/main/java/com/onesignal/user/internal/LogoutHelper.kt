package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.LoginUserOperation

class LogoutHelper(
    private val identityModelStore: IdentityModelStore,
    private val userSwitcher: UserSwitcher,
    private val operationRepo: IOperationRepo,
    private val configModel: ConfigModel,
    private val lock: Any,
) {
    fun logout() {
        synchronized(lock) {
            if (identityModelStore.model.externalId == null) {
                return
            }

            // Create new device-scoped user (clears external ID)
            userSwitcher.createAndSwitchToNewUser()

            // Enqueue login operation for the new device-scoped user (no external ID)
            operationRepo.enqueue(
                LoginUserOperation(
                    configModel.appId,
                    identityModelStore.model.onesignalId,
                    null,
                    // No external ID for device-scoped user
                ),
            )

            // TODO: remove JWT Token for all future requests.
        }
    }
}
