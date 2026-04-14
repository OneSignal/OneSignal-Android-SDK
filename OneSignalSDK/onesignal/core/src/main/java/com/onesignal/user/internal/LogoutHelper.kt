package com.onesignal.user.internal

import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore

class LogoutHelper(
    private val identityModelStore: IdentityModelStore,
    private val userSwitcher: UserSwitcher,
    private val operationRepo: IOperationRepo,
    private val configModel: ConfigModel,
    private val subscriptionModelStore: SubscriptionModelStore,
    private val lock: Any,
) {
    fun logout() {
        synchronized(lock) {
            if (identityModelStore.model.externalId == null) {
                return
            }

            if (configModel.useIdentityVerification == true) {
                configModel.pushSubscriptionId?.let { pushSubId ->
                    subscriptionModelStore.get(pushSubId)
                        ?.let { it.isDisabledInternally = true }
                }

                userSwitcher.createAndSwitchToNewUser(suppressBackendOperation = true)
            } else if (configModel.useIdentityVerification == false) {
                userSwitcher.createAndSwitchToNewUser()

                operationRepo.enqueue(
                    LoginUserOperation(
                        configModel.appId,
                        identityModelStore.model.onesignalId,
                        null,
                    ),
                )
            } else {
                // IV state unknown (pre-HYDRATE). Take the safe path: disable push
                // and suppress backend op (like IV=ON), but also enqueue a LoginUserOperation
                // so the anonymous user is created on the backend if IV turns out to be OFF.
                // If IV=ON, removeOperationsWithoutExternalId() will purge the anonymous op.
                configModel.pushSubscriptionId?.let { pushSubId ->
                    subscriptionModelStore.get(pushSubId)
                        ?.let { it.isDisabledInternally = true }
                }

                userSwitcher.createAndSwitchToNewUser(suppressBackendOperation = true)

                operationRepo.enqueue(
                    LoginUserOperation(
                        configModel.appId,
                        identityModelStore.model.onesignalId,
                        null,
                    ),
                )
            }
        }
    }
}
