package com.onesignal.user.internal.builduser.impl

import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.Operation
import com.onesignal.user.internal.builduser.IRebuildUserService
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.CreateSubscriptionOperation
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.operations.RefreshUserOperation
import com.onesignal.user.internal.operations.impl.listeners.SubscriptionModelStoreListener
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore

class RebuildUserService(
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _subscriptionsModelStore: SubscriptionModelStore,
    private val _configModelStore: ConfigModelStore,
) : IRebuildUserService {
    override fun getRebuildOperationsIfCurrentUser(
        appId: String,
        onesignalId: String,
    ): List<Operation>? {
        // make a copy of the current models
        val identityModel = IdentityModel()
        identityModel.initializeFromModel(null, _identityModelStore.model)

        val propertiesModel = PropertiesModel()
        propertiesModel.initializeFromModel(null, _propertiesModelStore.model)

        val subscriptionModels = mutableListOf<SubscriptionModel>()
        for (activeSubscriptionModel in _subscriptionsModelStore.list()) {
            val subscriptionModel = SubscriptionModel()
            subscriptionModel.initializeFromModel(null, activeSubscriptionModel)
            subscriptionModels.add(subscriptionModel)
        }

        // if the current models are no longer the onesignalId that needs rebuilding, we are done.
        if (identityModel.onesignalId != onesignalId) {
            return null
        }

        // rebuild the user.  Rebuilding is essentially the push subscription.
        val operations = mutableListOf<Operation>()

        operations.add(LoginUserOperation(appId, onesignalId, identityModel.externalId))
        val pushSubscription = subscriptionModels.firstOrNull { it.id == _configModelStore.model.pushSubscriptionId }
        if (pushSubscription != null) {
            operations.add(buildPushRecoveryOperation(appId, onesignalId, identityModel.externalId, pushSubscription))
        }
        operations.add(RefreshUserOperation(appId, onesignalId, identityModel.externalId))
        return operations
    }

    // The server records this rebuild recreates no longer exist, so a recorded REST API
    // disable died with them; clear it and recreate from device truth.
    private fun buildPushRecoveryOperation(
        appId: String,
        onesignalId: String,
        externalId: String?,
        pushSubscription: SubscriptionModel,
    ): CreateSubscriptionOperation {
        _subscriptionsModelStore.get(pushSubscription.id)?.setIntProperty(
            SubscriptionModel::restApiDisabledReason.name,
            0,
            ModelChangeTags.HYDRATE,
        )
        pushSubscription.restApiDisabledReason = 0
        val (enabled, status) = SubscriptionModelStoreListener.getSubscriptionEnabledAndStatus(pushSubscription)
        return CreateSubscriptionOperation(
            appId,
            onesignalId,
            externalId,
            pushSubscription.id,
            pushSubscription.type,
            enabled,
            pushSubscription.address,
            status,
        )
    }
}
