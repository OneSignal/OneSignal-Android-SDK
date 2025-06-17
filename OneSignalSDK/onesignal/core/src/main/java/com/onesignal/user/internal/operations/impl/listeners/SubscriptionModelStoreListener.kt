package com.onesignal.user.internal.operations.impl.listeners

import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.operations.listeners.ModelStoreListener
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.CreateSubscriptionOperation
import com.onesignal.user.internal.operations.DeleteSubscriptionOperation
import com.onesignal.user.internal.operations.UpdateSubscriptionOperation
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus

internal class SubscriptionModelStoreListener(
    store: SubscriptionModelStore,
    opRepo: IOperationRepo,
    private val _identityModelStore: IdentityModelStore,
    private val _configModelStore: ConfigModelStore,
) : ModelStoreListener<SubscriptionModel>(store, opRepo) {
    override fun getAddOperation(model: SubscriptionModel): Operation {
        val enabledAndStatus = getSubscriptionEnabledAndStatus(model, _identityModelStore, _configModelStore)
        return CreateSubscriptionOperation(
            _configModelStore.model.appId,
            _identityModelStore.model.onesignalId,
            model.id,
            model.type,
            enabledAndStatus.first,
            model.address,
            enabledAndStatus.second,
        )
    }

    override fun getRemoveOperation(model: SubscriptionModel): Operation {
        return DeleteSubscriptionOperation(_configModelStore.model.appId, _identityModelStore.model.onesignalId, model.id, model.type, model.address)
    }

    override fun getUpdateOperation(
        model: SubscriptionModel,
        path: String,
        property: String,
        oldValue: Any?,
        newValue: Any?,
    ): Operation {
        val enabledAndStatus = getSubscriptionEnabledAndStatus(model, _identityModelStore, _configModelStore)
        return UpdateSubscriptionOperation(
            _configModelStore.model.appId,
            _identityModelStore.model.onesignalId,
            model.id,
            model.type,
            enabledAndStatus.first,
            model.address,
            enabledAndStatus.second,
        )
    }

    companion object {
        fun getSubscriptionEnabledAndStatus(
            model: SubscriptionModel,
            identityModelStore: IdentityModelStore,
            configModelStore: ConfigModelStore,
        ): Pair<Boolean, SubscriptionStatus> {
            val status: SubscriptionStatus
            val enabled: Boolean

            /*
                When identity verification is off, we can enable the subscription regardless of the login status.
                When identity verification is on, the subscription is enabled only when a user is currently logged in.
             */
            val isUserLoggedInWhenIdentityRequired = !configModelStore.model.useIdentityVerification || !identityModelStore.model.externalId.isNullOrEmpty()
            if (isUserLoggedInWhenIdentityRequired && model.optedIn && model.status == SubscriptionStatus.SUBSCRIBED && model.address.isNotEmpty()) {
                enabled = true
                status = SubscriptionStatus.SUBSCRIBED
            } else {
                enabled = false
                status =
                    if (!model.optedIn) {
                        SubscriptionStatus.UNSUBSCRIBE
                    } else {
                        model.status
                    }
            }

            return Pair(enabled, status)
        }
    }
}
