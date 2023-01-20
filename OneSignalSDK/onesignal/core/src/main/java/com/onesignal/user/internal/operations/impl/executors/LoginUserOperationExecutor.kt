package com.onesignal.user.internal.operations.impl.executors

import android.os.Build
import com.onesignal.common.AndroidUtils
import com.onesignal.common.DeviceUtils
import com.onesignal.common.NetworkUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.RootToolsInternalMethods
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.backend.PropertiesObject
import com.onesignal.user.internal.backend.SubscriptionObject
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.CreateSubscriptionOperation
import com.onesignal.user.internal.operations.DeleteAliasOperation
import com.onesignal.user.internal.operations.DeleteSubscriptionOperation
import com.onesignal.user.internal.operations.DeleteTagOperation
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.operations.SetAliasOperation
import com.onesignal.user.internal.operations.SetPropertyOperation
import com.onesignal.user.internal.operations.SetTagOperation
import com.onesignal.user.internal.operations.UpdateSubscriptionOperation
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionType

internal class LoginUserOperationExecutor(
    private val _identityOperationExecutor: IdentityOperationExecutor,
    private val _application: IApplicationService,
    private val _deviceService: IDeviceService,
    private val _userBackend: IUserBackendService,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _subscriptionsModelStore: SubscriptionModelStore,
    private val _configModelStore: ConfigModelStore
) : IOperationExecutor {

    override val operations: List<String>
        get() = listOf(LOGIN_USER)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse {
        Logging.debug("LoginUserOperationExecutor(operation: $operations)")

        val startingOp = operations.first()

        if (startingOp is LoginUserOperation) {
            return loginUser(startingOp, operations)
        }

        throw Exception("Unrecognized operation: $startingOp")
    }

    private suspend fun loginUser(loginUserOp: LoginUserOperation, operations: List<Operation>): ExecutionResponse {
        if (loginUserOp.existingOnesignalId == null || loginUserOp.externalId == null) {
            // When there is no existing user to attempt to associate with the externalId provided, we go right to
            // createUser.  If there is no externalId provided this is an insert, if there is this will be an
            // "upsert with retrieval" as the user may already exist.
            return createUser(loginUserOp, operations)
        } else {
            // before we create a user we attempt to associate the user defined by existingOnesignalId with the
            // externalId provided. If that association cannot be made, typically because the externalId is already
            // associated to a user, we fall back to our "upsert with retrieval" method.
            val result = _identityOperationExecutor.execute(listOf(SetAliasOperation(loginUserOp.appId, loginUserOp.existingOnesignalId!!, IdentityConstants.EXTERNAL_ID, loginUserOp.externalId!!)))

            return when (result.result) {
                ExecutionResult.SUCCESS -> {
                    val backendOneSignalId = loginUserOp.existingOnesignalId!!
                    // because the set alias was successful any grouped operations could not be executed, let the
                    // caller know those still need to be executed.
                    if (_identityModelStore.model.onesignalId == loginUserOp.onesignalId) {
                        _identityModelStore.model.setStringProperty(IdentityConstants.ONESIGNAL_ID, backendOneSignalId, ModelChangeTags.HYDRATE)
                    }

                    if (_propertiesModelStore.model.onesignalId == loginUserOp.onesignalId) {
                        _propertiesModelStore.model.setStringProperty(PropertiesModel::onesignalId.name, backendOneSignalId, ModelChangeTags.HYDRATE)
                    }

                    ExecutionResponse(ExecutionResult.SUCCESS_STARTING_ONLY, mapOf(loginUserOp.onesignalId to backendOneSignalId))
                }
                ExecutionResult.FAIL_NORETRY -> {
                    // When the SetAliasOperation fails without retry that *most likely* means the externalId provided
                    // is already associated to a user.  This expected condition means we must create a user.
                    createUser(loginUserOp, operations)
                }
                else -> ExecutionResponse(result.result)
            }
        }
    }

    private suspend fun createUser(createUserOperation: LoginUserOperation, operations: List<Operation>): ExecutionResponse {
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
                is SetTagOperation -> propertiesObject = PropertyOperationHelper.createPropertiesFromOperation(operation, propertiesObject)
                is DeleteTagOperation -> propertiesObject = PropertyOperationHelper.createPropertiesFromOperation(operation, propertiesObject)
                is SetPropertyOperation -> propertiesObject = PropertyOperationHelper.createPropertiesFromOperation(operation, propertiesObject)
            }
        }

        try {
            val subscriptionList = subscriptions.values.toList()
            val response = _userBackend.createUser(createUserOperation.appId, identities, propertiesObject, subscriptionList)
            val idTranslations = mutableMapOf<String, String>()
            // Add the "local-to-backend" ID translation to the IdentifierTranslator for any operations that were
            // *not* executed but still reference the locally-generated IDs.
            // Update the current identity, property, and subscription models from a local ID to the backend ID
            val backendOneSignalId = response.identities[IdentityConstants.ONESIGNAL_ID]!!

            idTranslations[createUserOperation.onesignalId] = backendOneSignalId

            val identityModel = _identityModelStore.model
            val propertiesModel = _propertiesModelStore.model

            if (identityModel.onesignalId == createUserOperation.onesignalId) {
                identityModel.setStringProperty(IdentityConstants.ONESIGNAL_ID, backendOneSignalId, ModelChangeTags.HYDRATE)
            }

            if (propertiesModel.onesignalId == createUserOperation.onesignalId) {
                propertiesModel.setStringProperty(PropertiesModel::onesignalId.name, backendOneSignalId, ModelChangeTags.HYDRATE)
            }

            for (index in subscriptionList.indices) {
                if (index >= response.subscriptions.size) {
                    break
                }

                val backendSubscription = response.subscriptions[index]

                idTranslations[subscriptionList[index].id] = backendSubscription.id

                if (_configModelStore.model.pushSubscriptionId == subscriptionList[index].id) {
                    _configModelStore.model.pushSubscriptionId = backendSubscription.id
                }

                val subscriptionModel = _subscriptionsModelStore.get(subscriptionList[index].id)
                subscriptionModel?.setStringProperty(SubscriptionModel::id.name, backendSubscription.id, ModelChangeTags.HYDRATE)
            }

            return ExecutionResponse(ExecutionResult.SUCCESS, idTranslations)
        } catch (ex: BackendException) {
            val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)

            return when (responseType) {
                NetworkUtils.ResponseStatusType.RETRYABLE ->
                    ExecutionResponse(ExecutionResult.FAIL_RETRY)
                NetworkUtils.ResponseStatusType.UNAUTHORIZED ->
                    ExecutionResponse(ExecutionResult.FAIL_UNAUTHORIZED)
                else ->
                    ExecutionResponse(ExecutionResult.FAIL_NORETRY)
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
            operation.status.value,
            OneSignalUtils.sdkVersion,
            Build.MODEL,
            Build.VERSION.RELEASE,
            RootToolsInternalMethods.isRooted,
            DeviceUtils.getNetType(_application.appContext),
            DeviceUtils.getCarrierName(_application.appContext),
            AndroidUtils.getAppVersion(_application.appContext)
        )

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
                operation.status.value,
                subscriptions[operation.subscriptionId]!!.sdk,
                subscriptions[operation.subscriptionId]!!.deviceModel,
                subscriptions[operation.subscriptionId]!!.deviceOS,
                subscriptions[operation.subscriptionId]!!.rooted,
                subscriptions[operation.subscriptionId]!!.netType,
                subscriptions[operation.subscriptionId]!!.carrier,
                subscriptions[operation.subscriptionId]!!.appVersion
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

    companion object {
        const val LOGIN_USER = "login-user"
    }
}
