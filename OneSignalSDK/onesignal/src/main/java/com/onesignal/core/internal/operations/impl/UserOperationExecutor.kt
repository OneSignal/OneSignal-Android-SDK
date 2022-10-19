package com.onesignal.core.internal.operations.impl

import android.os.Build
import com.onesignal.core.debug.LogLevel
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.backend.BackendException
import com.onesignal.core.internal.backend.IUserBackendService
import com.onesignal.core.internal.backend.IdentityConstants
import com.onesignal.core.internal.backend.PropertiesDeltasObject
import com.onesignal.core.internal.backend.PropertiesObject
import com.onesignal.core.internal.backend.PurchaseObject
import com.onesignal.core.internal.backend.SubscriptionObject
import com.onesignal.core.internal.backend.SubscriptionObjectType
import com.onesignal.core.internal.common.DeviceUtils
import com.onesignal.core.internal.common.IDManager
import com.onesignal.core.internal.common.OneSignalUtils
import com.onesignal.core.internal.common.RootToolsInternalMethods
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.logging.Logging
import com.onesignal.core.internal.modeling.ModelChangeTags
import com.onesignal.core.internal.models.IdentityModelStore
import com.onesignal.core.internal.models.PropertiesModel
import com.onesignal.core.internal.models.PropertiesModelStore
import com.onesignal.core.internal.models.SubscriptionModel
import com.onesignal.core.internal.models.SubscriptionModelStore
import com.onesignal.core.internal.models.SubscriptionType
import com.onesignal.core.internal.operations.CreateSubscriptionOperation
import com.onesignal.core.internal.operations.CreateUserOperation
import com.onesignal.core.internal.operations.DeleteAliasOperation
import com.onesignal.core.internal.operations.DeleteSubscriptionOperation
import com.onesignal.core.internal.operations.DeleteTagOperation
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.core.internal.operations.SetAliasOperation
import com.onesignal.core.internal.operations.SetPropertyOperation
import com.onesignal.core.internal.operations.SetTagOperation
import com.onesignal.core.internal.operations.TrackPurchaseOperation
import com.onesignal.core.internal.operations.TrackSessionOperation
import com.onesignal.core.internal.operations.UpdateSubscriptionOperation
import java.util.UUID

internal class UserOperationExecutor(
    private val _application: IApplicationService,
    private val _deviceService: IDeviceService,
    private val _userBackend: IUserBackendService,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _subscriptionsModelStore: SubscriptionModelStore
) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(CREATE_USER, SET_TAG, DELETE_TAG, SET_PROPERTY, TRACK_SESSION, TRACK_PURCHASE)

    override suspend fun execute(operations: List<Operation>) {
        Logging.log(LogLevel.DEBUG, "UserOperationExecutor(operation: $operations)")

        val startingOp = operations.first()

        if (startingOp is CreateUserOperation) {
            createUser(startingOp, operations)
        } else {
            updateUser(operations)
        }
    }

    private suspend fun createUser(createUserOperation: CreateUserOperation, operations: List<Operation>) {
        var identities = mapOf<String, String>()
        var propertiesObject = PropertiesObject()
        var subscriptions = mapOf<String, SubscriptionObject>()

        if (createUserOperation.externalId != null) {
            val mutableIdentities = identities.toMutableMap()
            mutableIdentities[IdentityConstants.EXTERNAL_ID] = createUserOperation.externalId!!
            identities = mutableIdentities
        }

        // go through the operations grouped with this create user and apply them to the appropriate objects.
        for (operation in operations) {
            when (operation) {
                is SetAliasOperation -> identities = createIdentityFromOperation(operation, identities)
                is DeleteAliasOperation -> identities = createIdentityFromOperation(operation, identities)
                is CreateSubscriptionOperation -> subscriptions = createSubscriptionsFromOperation(operation, subscriptions)
                is UpdateSubscriptionOperation -> subscriptions = createSubscriptionsFromOperation(operation, subscriptions)
                is DeleteSubscriptionOperation -> subscriptions = createSubscriptionsFromOperation(operation, subscriptions)
                is SetTagOperation -> propertiesObject = createPropertiesFromOperation(operation, propertiesObject)
                is DeleteTagOperation -> propertiesObject = createPropertiesFromOperation(operation, propertiesObject)
                is SetPropertyOperation -> propertiesObject = createPropertiesFromOperation(operation, propertiesObject)
            }
        }

        try {
            val subscriptionList = subscriptions.values.toList()
            val response = _userBackend.createUser(createUserOperation.appId, identities, propertiesObject, subscriptionList)

            // Add the "local-to-backend" ID translation to the IdentifierTranslator for any operations that were
            // *not* executed but still reference the locally-generated IDs.
            // Update the current identity, property, and subscription models from a local ID to the backend ID
            val backendOneSignalId = response.identities[IdentityConstants.ONESIGNAL_ID]!!

            IDManager.setLocalToBackendIdTranslation(createUserOperation.onesignalId, backendOneSignalId)

            val identityModel = _identityModelStore.model
            val propertiesModel = _propertiesModelStore.model

            if (identityModel.onesignalId == createUserOperation.onesignalId) {
                identityModel.setProperty(IdentityConstants.ONESIGNAL_ID, backendOneSignalId, ModelChangeTags.HYDRATE)

                // TODO: hydrate any additional aliases from the backend...
            }

            if (propertiesModel.onesignalId == createUserOperation.onesignalId) {
                propertiesModel.setProperty(PropertiesModel::onesignalId.name, backendOneSignalId, ModelChangeTags.HYDRATE)

                // TODO: hydrate the models from the backend create response.  Temporarily inject dummy stuff to
                //       show that it's working.
//                propertiesModel.setProperty(PropertiesModel::language.name, "en", notify = false)
                propertiesModel.setProperty(PropertiesModel::country.name, "US", ModelChangeTags.HYDRATE)
                propertiesModel.tags.setProperty("foo", UUID.randomUUID().toString(), ModelChangeTags.HYDRATE)
            }

            // TODO: assumption that the response.subscriptionIDs will associate to the input subscriptionList...to confirm
            for (index in subscriptionList.indices) {
                if (index >= response.subscriptionIDs.size) {
                    break
                }

                val backendSubscriptionId = response.subscriptionIDs[index]

                IDManager.setLocalToBackendIdTranslation(subscriptionList[index].id, backendSubscriptionId)

                val subscriptionModel = _subscriptionsModelStore.get(subscriptionList[index].id)
                subscriptionModel?.setProperty(SubscriptionModel::id.name, backendSubscriptionId, ModelChangeTags.HYDRATE)
            }
        } catch (ex: BackendException) {
        }
    }

    private suspend fun updateUser(ops: List<Operation>) {
        var appId: String? = null
        var onesignalId: String? = null

        var propertiesObject = PropertiesObject()
        var deltasObject = PropertiesDeltasObject()

        for (operation in ops) {
            when (operation) {
                is SetTagOperation -> {
                    if (appId == null) {
                        appId = operation.appId
                        onesignalId = operation.onesignalId
                    }

                    propertiesObject = createPropertiesFromOperation(operation, propertiesObject)
                }
                is DeleteTagOperation -> {
                    if (appId == null) {
                        appId = operation.appId
                        onesignalId = operation.onesignalId
                    }

                    propertiesObject = createPropertiesFromOperation(operation, propertiesObject)
                }
                is SetPropertyOperation -> {
                    if (appId == null) {
                        appId = operation.appId
                        onesignalId = operation.onesignalId
                    }

                    propertiesObject = createPropertiesFromOperation(operation, propertiesObject)
                }
                is TrackSessionOperation -> {
                    if (appId == null) {
                        appId = operation.appId
                        onesignalId = operation.onesignalId
                    }

                    // The session count we pass up is essentially the number of `TrackSessionOperation`
                    // operations we come across in this group, while the session time we pass up is
                    // the total session time across all `TrackSessionOperation` operations
                    // that exist in this group.
                    val sessionTime = if (deltasObject.sessionTime != null) deltasObject.sessionTime!! + operation.sessionTime else operation.sessionTime
                    val sessionCount = if (deltasObject.sessionCounts != null) deltasObject.sessionCounts!! + 1 else 1

                    deltasObject = PropertiesDeltasObject(sessionTime, sessionCount, deltasObject.amountSpent, deltasObject.purchases)
                }
                is TrackPurchaseOperation -> {
                    if (appId == null) {
                        appId = operation.appId
                        onesignalId = operation.onesignalId
                    }

                    // The amount spent we pass up is the total amount spent across all `TrackPurchaseOperation`
                    // operations that exist in this group, while the purchases is the union of all
                    // `TrackPurchaseOperation` operations that exist in this group.
                    val amountSpent = if (deltasObject.amountSpent != null) deltasObject.amountSpent!! + operation.amountSpent else operation.amountSpent
                    val purchasesArray = if (deltasObject.purchases != null) deltasObject.purchases!!.toMutableList() else mutableListOf()

                    for (purchase in operation.purchases) {
                        purchasesArray.add(PurchaseObject(purchase.sku, purchase.iso, purchase.amount))
                    }

                    deltasObject = PropertiesDeltasObject(deltasObject.sessionTime, deltasObject.sessionCounts, amountSpent, purchasesArray)
                }
            }
        }

        if (appId != null && onesignalId != null) {
            try {
                _userBackend.updateUser(appId, IdentityConstants.ONESIGNAL_ID, IDManager.retrieveId(onesignalId), propertiesObject, true, deltasObject)
            } catch (ex: BackendException) {
                // TODO: Handle failure
            }
        }
    }

    private fun createIdentityFromOperation(operation: SetAliasOperation, identity: Map<String, String>): Map<String, String> {
        val mutableIdentity = identity.toMutableMap()
        mutableIdentity[operation.label] = operation.value
        return mutableIdentity
    }

    private fun createIdentityFromOperation(operation: DeleteAliasOperation, identity: Map<String, String>): Map<String, String> {
        val mutableIdentity = identity.toMutableMap()
        mutableIdentity.remove(operation.label)
        return mutableIdentity
    }

    private fun createSubscriptionsFromOperation(operation: CreateSubscriptionOperation, subscriptions: Map<String, SubscriptionObject>): Map<String, SubscriptionObject> {
        val mutableSubscriptions = subscriptions.toMutableMap()
        val subscriptionType: SubscriptionObjectType = when (operation.type) {
            SubscriptionType.SMS -> {
                SubscriptionObjectType.SMS
            }
            SubscriptionType.EMAIL -> {
                SubscriptionObjectType.SMS
            }
            else -> {
                SubscriptionObjectType.fromDeviceType(_deviceService.deviceType)
            }
        }
        mutableSubscriptions[operation.subscriptionId] = SubscriptionObject(
            operation.subscriptionId,
            subscriptionType,
            operation.address,
            operation.enabled,
            null, // TODO: What is this for?
            OneSignalUtils.sdkVersion,
            Build.MODEL,
            Build.VERSION.RELEASE,
            RootToolsInternalMethods.isRooted,
            null, // TODO: What is test_type?
            null,
            DeviceUtils.getNetType(_application.appContext),
            DeviceUtils.getCarrierName(_application.appContext),
            null, // TODO: Fill in
            null
        ) // TODO: Fill in

        // TODO: These are no longer captured?
        // subscriptionObject.put("sdk_type", OneSignalUtils.sdkType)
        // subscriptionObject.put("type", SubscriptionObjectType.fromDeviceType(_deviceService.deviceType))
        // subscriptionObject.put("android_package", _application.appContext.packageName)
//                        try {
//                            subscriptionObject.put("game_version", _application.appContext.packageManager.getPackageInfo(_application.appContext.packageName, 0).versionCode)
//                        } catch (e: PackageManager.NameNotFoundException) {
//                        }

        return mutableSubscriptions
    }

    private fun createSubscriptionsFromOperation(operation: UpdateSubscriptionOperation, subscriptions: Map<String, SubscriptionObject>): Map<String, SubscriptionObject> {
        val mutableSubscriptions = subscriptions.toMutableMap()
        if (mutableSubscriptions.containsKey(operation.subscriptionId)) {
            mutableSubscriptions[operation.subscriptionId] = SubscriptionObject(
                operation.subscriptionId,
                subscriptions[operation.subscriptionId]!!.type,
                operation.address,
                operation.enabled,
                subscriptions[operation.subscriptionId]!!.notificationTypes,
                subscriptions[operation.subscriptionId]!!.sdk,
                subscriptions[operation.subscriptionId]!!.deviceModel,
                subscriptions[operation.subscriptionId]!!.deviceOS,
                subscriptions[operation.subscriptionId]!!.rooted,
                subscriptions[operation.subscriptionId]!!.testType,
                subscriptions[operation.subscriptionId]!!.appVersion,
                subscriptions[operation.subscriptionId]!!.netType,
                subscriptions[operation.subscriptionId]!!.carrier,
                subscriptions[operation.subscriptionId]!!.webAuth,
                subscriptions[operation.subscriptionId]!!.webP256
            )
        }
        // TODO: Is it possible for the Create to be after the Update?
        return mutableSubscriptions
    }

    private fun createSubscriptionsFromOperation(operation: DeleteSubscriptionOperation, subscriptions: Map<String, SubscriptionObject>): Map<String, SubscriptionObject> {
        val mutableSubscriptions = subscriptions.toMutableMap()
        mutableSubscriptions.remove(operation.subscriptionId)
        return mutableSubscriptions
    }

    private fun createPropertiesFromOperation(operation: SetTagOperation, propertiesObject: PropertiesObject): PropertiesObject {
        var tags = propertiesObject.tags?.toMutableMap()
        if (tags == null) {
            tags = mutableMapOf()
        }

        tags[operation.key] = operation.value
        return PropertiesObject(tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude)
    }

    private fun createPropertiesFromOperation(operation: DeleteTagOperation, propertiesObject: PropertiesObject): PropertiesObject {
        var tags = propertiesObject.tags?.toMutableMap()
        tags?.remove(operation.key)
        return PropertiesObject(tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude)
    }

    private fun createPropertiesFromOperation(operation: SetPropertyOperation, propertiesObject: PropertiesObject): PropertiesObject {
        return when (operation.property) {
            PropertiesModel::language.name -> PropertiesObject(propertiesObject.tags, operation.value?.toString(), propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::timezone.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, operation.value?.toString(), propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::country.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, operation.value?.toString(), propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::locationLatitude.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, operation.value?.toString()?.toDouble(), propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::locationLongitude.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, operation.value?.toString()?.toDouble(), propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::locationAccuracy.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, operation.value?.toString()?.toFloat(), propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::locationType.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, operation.value?.toString()?.toInt(), propertiesObject.locationBackground, propertiesObject.locationTimestamp)
            PropertiesModel::locationBackground.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, operation.value?.toString()?.toBoolean(), propertiesObject.locationTimestamp)
            PropertiesModel::locationTimestamp.name -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, operation.value?.toString()?.toLong())
            else -> PropertiesObject(propertiesObject.tags, propertiesObject.language, propertiesObject.timezoneId, propertiesObject.country, propertiesObject.latitude, propertiesObject.longitude, propertiesObject.locationAccuracy, propertiesObject.locationType, propertiesObject.locationBackground, propertiesObject.locationTimestamp)
        }
    }

    companion object {
        const val CREATE_USER = "create-user"
        const val SET_TAG = "set-tag"
        const val DELETE_TAG = "delete-tag"
        const val SET_PROPERTY = "set-property"
        const val TRACK_SESSION = "track-session"
        const val TRACK_PURCHASE = "track-purchase"
    }
}
