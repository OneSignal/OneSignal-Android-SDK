package com.onesignal.user.internal

import android.content.Context
import com.onesignal.common.AndroidUtils
import com.onesignal.common.IDManager
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.safeInt
import com.onesignal.common.safeString
import com.onesignal.common.services.ServiceProvider
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.core.internal.preferences.clearLegacyPlayerId
import com.onesignal.core.internal.preferences.getLegacyPlayerId
import com.onesignal.core.internal.preferences.getLegacyUserSyncValues
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.identity.hasOneSignalId
import com.onesignal.user.internal.operations.LoginUserFromSubscriptionOperation
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType
import org.json.JSONObject

class UserSwitcher(
    private val preferencesService: IPreferencesService,
    private val operationRepo: IOperationRepo,
    private val services: ServiceProvider,
    private val idManager: IDManager = IDManager,
    private val identityModelStore: IdentityModelStore,
    private val propertiesModelStore: PropertiesModelStore,
    private val subscriptionModelStore: SubscriptionModelStore,
    private val configModel: ConfigModel,
    private val oneSignalUtils: OneSignalUtils = OneSignalUtils,
    private val carrierName: String? = null,
    private val deviceOS: String? = null,
    private val androidUtils: AndroidUtils = AndroidUtils,
    private val appContextProvider: () -> Context,
) {
    fun createAndSwitchToNewUser(
        suppressBackendOperation: Boolean = false,
        modify: ((identityModel: IdentityModel, propertiesModel: PropertiesModel) -> Unit)? = null,
    ) {
        Logging.debug("createAndSwitchToNewUser()")

        val sdkId = idManager.createLocalId()

        val identityModel = IdentityModel().apply { onesignalId = sdkId }
        val propertiesModel = PropertiesModel().apply { onesignalId = sdkId }

        modify?.invoke(identityModel, propertiesModel)

        val subscriptions = mutableListOf<SubscriptionModel>()
        val currentPushSubscription =
            subscriptionModelStore.list()
                .firstOrNull { it.id == configModel.pushSubscriptionId }
        val newPushSubscription =
            SubscriptionModel().apply {
                id = currentPushSubscription?.id ?: idManager.createLocalId()
                type = SubscriptionType.PUSH
                optedIn = currentPushSubscription?.optedIn ?: true
                address = currentPushSubscription?.address ?: ""
                status = currentPushSubscription?.status ?: SubscriptionStatus.NO_PERMISSION
                sdk = oneSignalUtils.sdkVersion
                deviceOS = this@UserSwitcher.deviceOS ?: ""
                carrier = carrierName ?: ""
                appVersion = androidUtils.getAppVersion(appContextProvider()) ?: ""
            }

        configModel.pushSubscriptionId = newPushSubscription.id
        subscriptions.add(newPushSubscription)

        subscriptionModelStore.clear(ModelChangeTags.NO_PROPOGATE)
        identityModelStore.replace(identityModel)
        propertiesModelStore.replace(propertiesModel)

        if (suppressBackendOperation) {
            subscriptionModelStore.replaceAll(subscriptions, ModelChangeTags.NO_PROPOGATE)
        } else {
            subscriptionModelStore.replaceAll(subscriptions)
        }
    }

    fun createPushSubscriptionFromLegacySync(
        legacyPlayerId: String,
        legacyUserSyncJSON: JSONObject,
        configModel: ConfigModel,
        subscriptionModelStore: SubscriptionModelStore,
        appContext: Context,
    ): Boolean {
        val notificationTypes = legacyUserSyncJSON.safeInt("notification_types")

        val pushSubscriptionModel =
            SubscriptionModel().apply {
                id = legacyPlayerId
                type = SubscriptionType.PUSH
                optedIn = notificationTypes != SubscriptionStatus.NO_PERMISSION.value &&
                    notificationTypes != SubscriptionStatus.UNSUBSCRIBE.value
                address = legacyUserSyncJSON.safeString("identifier") ?: ""
                status = notificationTypes?.let { SubscriptionStatus.fromInt(it) }
                    ?: SubscriptionStatus.SUBSCRIBED
                sdk = OneSignalUtils.sdkVersion
                deviceOS = this@UserSwitcher.deviceOS ?: ""
                carrier = carrierName ?: ""
                appVersion = AndroidUtils.getAppVersion(appContext) ?: ""
            }

        configModel.pushSubscriptionId = legacyPlayerId
        subscriptionModelStore.add(pushSubscriptionModel, ModelChangeTags.NO_PROPOGATE)
        return true
    }

    fun initUser(forceCreateUser: Boolean) {
        if (forceCreateUser || !identityModelStore.hasOneSignalId()) {
            val legacyPlayerId = preferencesService.getLegacyPlayerId()

            if (legacyPlayerId == null) {
                createNewUser()
            } else {
                migrateFromLegacyUser(legacyPlayerId)
            }
        } else {
            Logging.debug("initWithContext: using cached user ${identityModelStore.model.onesignalId}")
        }
    }

    /**
     * Creates a new device-scoped user with no legacy data.
     */
    private fun createNewUser() {
        Logging.debug("initWithContext: creating new device-scoped user")
        createAndSwitchToNewUser()
        operationRepo.enqueue(
            LoginUserOperation(
                configModel.appId,
                identityModelStore.model.onesignalId,
                identityModelStore.model.externalId,
            ),
        )
    }

    /**
     * Migrates from a v4 SDK user by creating a new user linked to the legacy subscription.
     * This handles the conversion from 4.x SDK to 5.x SDK format.
     */
    private fun migrateFromLegacyUser(legacyPlayerId: String) {
        Logging.debug("initWithContext: creating user linked to subscription $legacyPlayerId")

        val legacyUserSyncString = preferencesService.getLegacyUserSyncValues()
        var suppressBackendOperation = false

        if (legacyUserSyncString != null) {
            createPushSubscriptionFromLegacySync(
                legacyPlayerId = legacyPlayerId,
                legacyUserSyncJSON = JSONObject(legacyUserSyncString),
                configModel = configModel,
                subscriptionModelStore = subscriptionModelStore,
                appContext = services.getService<IApplicationService>().appContext,
            )
            suppressBackendOperation = true
        }

        createAndSwitchToNewUser(suppressBackendOperation = suppressBackendOperation)

        operationRepo.enqueue(
            LoginUserFromSubscriptionOperation(
                configModel.appId,
                identityModelStore.model.onesignalId,
                legacyPlayerId,
            ),
        )

        preferencesService.clearLegacyPlayerId()
    }
}
