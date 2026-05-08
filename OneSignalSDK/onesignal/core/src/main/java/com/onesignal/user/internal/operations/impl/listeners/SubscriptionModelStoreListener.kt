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
        val enabledAndStatus = getSubscriptionEnabledAndStatus(model)
        return CreateSubscriptionOperation(
            _configModelStore.model.appId,
            _identityModelStore.model.onesignalId,
            _identityModelStore.model.externalId,
            model.id,
            model.type,
            enabledAndStatus.first,
            model.address,
            enabledAndStatus.second,
        )
    }

    override fun getRemoveOperation(model: SubscriptionModel): Operation {
        return DeleteSubscriptionOperation(
            _configModelStore.model.appId,
            _identityModelStore.model.onesignalId,
            _identityModelStore.model.externalId,
            model.id,
        )
    }

    override fun getUpdateOperation(
        model: SubscriptionModel,
        path: String,
        property: String,
        oldValue: Any?,
        newValue: Any?,
    ): Operation {
        val enabledAndStatus = getSubscriptionEnabledAndStatus(model)
        return UpdateSubscriptionOperation(
            _configModelStore.model.appId,
            _identityModelStore.model.onesignalId,
            _identityModelStore.model.externalId,
            model.id,
            model.type,
            enabledAndStatus.first,
            model.address,
            enabledAndStatus.second,
        )
    }

    companion object {
        fun getSubscriptionEnabledAndStatus(model: SubscriptionModel): Pair<Boolean, SubscriptionStatus> {
            // Internal-disabled subscription (e.g. the post-logout anonymous user under IV)
            // must not generate backend ops; report as disabled+unsubscribe regardless of
            // optedIn/status. See [SubscriptionModel.isDisabledInternally].
            if (model.isDisabledInternally) {
                return Pair(false, SubscriptionStatus.UNSUBSCRIBE)
            }

            val status: SubscriptionStatus
            val enabled: Boolean

            if (model.optedIn && model.status == SubscriptionStatus.SUBSCRIBED && model.address.isNotEmpty()) {
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
