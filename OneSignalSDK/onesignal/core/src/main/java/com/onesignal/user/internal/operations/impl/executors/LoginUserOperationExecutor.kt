package com.onesignal.user.internal.operations.impl.executors

import android.os.Build
import com.onesignal.common.AndroidUtils
import com.onesignal.common.DeviceUtils
import com.onesignal.common.NetworkUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.RootToolsInternalMethods
import com.onesignal.common.TimeUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.backend.SubscriptionObject
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.CreateSubscriptionOperation
import com.onesignal.user.internal.operations.DeleteSubscriptionOperation
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.operations.RefreshUserOperation
import com.onesignal.user.internal.operations.SetAliasOperation
import com.onesignal.user.internal.operations.TransferSubscriptionOperation
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
    private val _configModelStore: ConfigModelStore,
    private val _languageContext: ILanguageContext,
) : IOperationExecutor {
    override val operations: List<String>
        get() = listOf(LOGIN_USER)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse {
        Logging.debug("LoginUserOperationExecutor(operation: $operations)")

        val startingOp = operations.first()

        if (startingOp is LoginUserOperation) {
            return loginUser(startingOp, operations.drop(1))
        }

        throw Exception("Unrecognized operation: $startingOp")
    }

    private suspend fun loginUser(
        loginUserOp: LoginUserOperation,
        operations: List<Operation>,
    ): ExecutionResponse {
        if (loginUserOp.existingOnesignalId == null || loginUserOp.externalId == null) {
            // When there is no existing user to attempt to associate with the externalId provided, we go right to
            // createUser.  If there is no externalId provided this is an insert, if there is this will be an
            // "upsert with retrieval" as the user may already exist.
            return createUser(loginUserOp, operations)
        } else {
            // before we create a user we attempt to associate the user defined by existingOnesignalId with the
            // externalId provided. If that association cannot be made, typically because the externalId is already
            // associated to a user, we fall back to our "upsert with retrieval" method.
            val result =
                _identityOperationExecutor.execute(
                    listOf(
                        SetAliasOperation(
                            loginUserOp.appId,
                            loginUserOp.existingOnesignalId!!,
                            IdentityConstants.EXTERNAL_ID,
                            loginUserOp.externalId!!,
                        ),
                    ),
                )

            return when (result.result) {
                ExecutionResult.SUCCESS -> {
                    val backendOneSignalId = loginUserOp.existingOnesignalId!!
                    // because the set alias was successful any grouped operations could not be executed, let the
                    // caller know those still need to be executed.
                    if (_identityModelStore.model.onesignalId == loginUserOp.onesignalId) {
                        _identityModelStore.model.setStringProperty(
                            IdentityConstants.ONESIGNAL_ID,
                            backendOneSignalId,
                            ModelChangeTags.HYDRATE,
                        )
                    }

                    if (_propertiesModelStore.model.onesignalId == loginUserOp.onesignalId) {
                        _propertiesModelStore.model.setStringProperty(
                            PropertiesModel::onesignalId.name,
                            backendOneSignalId,
                            ModelChangeTags.HYDRATE,
                        )
                    }

                    ExecutionResponse(ExecutionResult.SUCCESS_STARTING_ONLY, mapOf(loginUserOp.onesignalId to backendOneSignalId))
                }
                ExecutionResult.FAIL_CONFLICT -> {
                    // When the SetAliasOperation fails with conflict that *most likely* means the externalId provided
                    // is already associated to a user.  This *expected* condition means we must create a user.
                    // We hardcode the response of "user-2" in the log to provide information to the SDK consumer
                    Logging.debug(
                        "LoginUserOperationExecutor now handling 409 response with \"code\": \"user-2\" by switching to user with \"external_id\": \"${loginUserOp.externalId}\"",
                    )
                    createUser(loginUserOp, operations)
                }
                ExecutionResult.FAIL_NORETRY -> {
                    // Some other failure occurred, still try to recover by creating the user
                    Logging.error(
                        "LoginUserOperationExecutor encountered error. Attempt to recover by switching to user with \"external_id\": \"${loginUserOp.externalId}\"",
                    )
                    createUser(loginUserOp, operations)
                }
                ExecutionResult.FAIL_UNAUTHORIZED -> {
                    _identityModelStore.model.setStringProperty(
                            IdentityConstants.JWT_TOKEN,
                            "",
                    )
                    ExecutionResponse(result.result)
                }
                else -> ExecutionResponse(result.result)
            }
        }
    }

    private suspend fun createUser(
        createUserOperation: LoginUserOperation,
        operations: List<Operation>,
    ): ExecutionResponse {
        var identities = mapOf<String, String>()
        var subscriptions = mapOf<String, SubscriptionObject>()
        val properties = mutableMapOf<String, String>()
        properties["timezone_id"] = TimeUtils.getTimeZoneId()!!
        properties["language"] = _languageContext.language

        if (createUserOperation.externalId != null) {
            val mutableIdentities = identities.toMutableMap()
            mutableIdentities[IdentityConstants.EXTERNAL_ID] = createUserOperation.externalId!!
            identities = mutableIdentities
        }

        // go through the operations grouped with this create user and apply them to the appropriate objects.
        for (operation in operations) {
            when (operation) {
                is CreateSubscriptionOperation -> subscriptions = createSubscriptionsFromOperation(operation, subscriptions)
                is TransferSubscriptionOperation -> subscriptions = createSubscriptionsFromOperation(operation, subscriptions)
                is UpdateSubscriptionOperation -> subscriptions = createSubscriptionsFromOperation(operation, subscriptions)
                is DeleteSubscriptionOperation -> subscriptions = createSubscriptionsFromOperation(operation, subscriptions)
                else -> throw Exception("Unrecognized operation: $operation")
            }
        }

        try {
            val subscriptionList = subscriptions.toList()
            val response = _userBackend.createUser(createUserOperation.appId, identities, subscriptionList.map { it.second }, properties, _identityModelStore.model.jwtToken)
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

                idTranslations[subscriptionList[index].first] = backendSubscription.id!!

                if (_configModelStore.model.pushSubscriptionId == subscriptionList[index].first) {
                    _configModelStore.model.pushSubscriptionId = backendSubscription.id
                }

                val subscriptionModel = _subscriptionsModelStore.get(subscriptionList[index].first)
                subscriptionModel?.setStringProperty(SubscriptionModel::id.name, backendSubscription.id, ModelChangeTags.HYDRATE)
            }

            val wasPossiblyAnUpsert = identities.isNotEmpty()
            val followUpOperations =
                if (wasPossiblyAnUpsert) {
                    listOf(RefreshUserOperation(createUserOperation.appId, backendOneSignalId))
                } else {
                    null
                }

            return ExecutionResponse(ExecutionResult.SUCCESS, idTranslations, followUpOperations)
        } catch (ex: BackendException) {
            val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)

            return when (responseType) {
                NetworkUtils.ResponseStatusType.RETRYABLE ->
                    ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                NetworkUtils.ResponseStatusType.UNAUTHORIZED ->
                    ExecutionResponse(ExecutionResult.FAIL_UNAUTHORIZED, retryAfterSeconds = ex.retryAfterSeconds)
                else ->
                    ExecutionResponse(ExecutionResult.FAIL_PAUSE_OPREPO)
            }
        }
    }

    private fun createSubscriptionsFromOperation(
        operation: TransferSubscriptionOperation,
        subscriptions: Map<String, SubscriptionObject>,
    ): Map<String, SubscriptionObject> {
        val mutableSubscriptions = subscriptions.toMutableMap()
        if (mutableSubscriptions.containsKey(operation.subscriptionId)) {
            mutableSubscriptions[operation.subscriptionId] =
                SubscriptionObject(
                    operation.subscriptionId,
                    subscriptions[operation.subscriptionId]!!.type,
                    subscriptions[operation.subscriptionId]!!.token,
                    subscriptions[operation.subscriptionId]!!.enabled,
                    subscriptions[operation.subscriptionId]!!.notificationTypes,
                    subscriptions[operation.subscriptionId]!!.sdk,
                    subscriptions[operation.subscriptionId]!!.deviceModel,
                    subscriptions[operation.subscriptionId]!!.deviceOS,
                    subscriptions[operation.subscriptionId]!!.rooted,
                    subscriptions[operation.subscriptionId]!!.netType,
                    subscriptions[operation.subscriptionId]!!.carrier,
                    subscriptions[operation.subscriptionId]!!.appVersion,
                )
        } else {
            mutableSubscriptions[operation.subscriptionId] = SubscriptionObject(operation.subscriptionId)
        }

        return mutableSubscriptions
    }

    private fun createSubscriptionsFromOperation(
        operation: CreateSubscriptionOperation,
        subscriptions: Map<String, SubscriptionObject>,
    ): Map<String, SubscriptionObject> {
        val mutableSubscriptions = subscriptions.toMutableMap()
        val subscriptionType: SubscriptionObjectType =
            when (operation.type) {
                SubscriptionType.SMS -> {
                    SubscriptionObjectType.SMS
                }
                SubscriptionType.EMAIL -> {
                    SubscriptionObjectType.EMAIL
                }
                else -> {
                    SubscriptionObjectType.fromDeviceType(_deviceService.deviceType)
                }
            }
        mutableSubscriptions[operation.subscriptionId] =
            SubscriptionObject(
                id = null,
                subscriptionType,
                operation.address,
                operation.enabled,
                operation.status.value,
                OneSignalUtils.SDK_VERSION,
                Build.MODEL,
                Build.VERSION.RELEASE,
                RootToolsInternalMethods.isRooted,
                DeviceUtils.getNetType(_application.appContext),
                DeviceUtils.getCarrierName(_application.appContext),
                AndroidUtils.getAppVersion(_application.appContext),
            )

        return mutableSubscriptions
    }

    private fun createSubscriptionsFromOperation(
        operation: UpdateSubscriptionOperation,
        subscriptions: Map<String, SubscriptionObject>,
    ): Map<String, SubscriptionObject> {
        val mutableSubscriptions = subscriptions.toMutableMap()
        if (mutableSubscriptions.containsKey(operation.subscriptionId)) {
            mutableSubscriptions[operation.subscriptionId] =
                SubscriptionObject(
                    subscriptions[operation.subscriptionId]!!.id,
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
                    subscriptions[operation.subscriptionId]!!.appVersion,
                )
        }

        return mutableSubscriptions
    }

    private fun createSubscriptionsFromOperation(
        operation: DeleteSubscriptionOperation,
        subscriptions: Map<String, SubscriptionObject>,
    ): Map<String, SubscriptionObject> {
        val mutableSubscriptions = subscriptions.toMutableMap()
        mutableSubscriptions.remove(operation.subscriptionId)
        return mutableSubscriptions
    }

    companion object {
        const val LOGIN_USER = "login-user"
    }
}
