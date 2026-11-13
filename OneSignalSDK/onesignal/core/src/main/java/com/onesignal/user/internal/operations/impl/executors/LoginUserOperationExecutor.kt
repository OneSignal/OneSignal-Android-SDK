package com.onesignal.user.internal.operations.impl.executors

import android.os.Build
import com.onesignal.OneSignalError
import com.onesignal.common.AndroidUtils
import com.onesignal.common.DeviceUtils
import com.onesignal.common.IDManager
import com.onesignal.common.NetworkUtils
import com.onesignal.common.OneSignalUtils
import com.onesignal.common.PIIHasher
import com.onesignal.common.RootToolsInternalMethods
import com.onesignal.common.TimeUtils
import com.onesignal.common.consistency.enums.IamFetchRywTokenKey
import com.onesignal.common.consistency.models.IConsistencyManager
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.hasNullByte
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.config.impl.IdentityVerificationService
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.language.ILanguageContext
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.LoginWaitMetadata
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.backend.PropertiesObject
import com.onesignal.user.internal.backend.SubscriptionObject
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.internal.operations.CreateSubscriptionOperation
import com.onesignal.user.internal.operations.DeleteSubscriptionOperation
import com.onesignal.user.internal.operations.LoginUserOperation
import com.onesignal.user.internal.operations.RefreshUserOperation
import com.onesignal.user.internal.operations.SetAliasOperation
import com.onesignal.user.internal.operations.TransferSubscriptionOperation
import com.onesignal.user.internal.operations.UpdateSubscriptionOperation
import com.onesignal.user.internal.operations.reservedLoginAliasLabel
import com.onesignal.user.internal.operations.sameSubscriptionAddress
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType

@Suppress("TooManyFunctions")
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
    private val _jwtTokenStore: JwtTokenStore,
    private val _identityVerificationService: IdentityVerificationService,
    private val _consistencyManager: IConsistencyManager,
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
        val alias = resolveAlias(loginUserOp)

        // Handle a bad state that can happen in User Model 5.1.27 or earlier versions that old Login
        // request is not removed after processing if app is force-closed within the PostCreateDelay.
        // Anonymous Login being processed alone will surely be rejected, so we need to drop the request
        val containsSubscriptionOperation = operations.any { it is CreateSubscriptionOperation || it is TransferSubscriptionOperation }
        if (!containsSubscriptionOperation && alias == null) {
            return ExecutionResponse(ExecutionResult.FAIL_NORETRY)
        }
        if (shouldCreateUserDirectly(loginUserOp, alias)) {
            // A server anonymous user is merged by sending its onesignal_id. Under IV, skip
            // SetAlias: alias resolution would target the new external_id and 404 or hit the wrong user.
            anonymousUserToMerge(loginUserOp, alias)?.let { anonymousId ->
                val merged = createUser(loginUserOp, operations, anonymousId)
                if (!shouldAbandonMerge(merged)) return merged
            }
            return createUser(loginUserOp, operations)
        } else {
            // before we create a user we attempt to associate the user defined by existingOnesignalId with the
            // externalId provided. If that association cannot be made, typically because the externalId is already
            // associated to a user, we fall back to our "upsert with retrieval" method.
            val mergeAlias = alias!!
            val result =
                _identityOperationExecutor.execute(
                    listOf(
                        SetAliasOperation(
                            loginUserOp.appId,
                            loginUserOp.existingOnesignalId!!,
                            mergeAlias,
                            IdentityConstants.EXTERNAL_ID,
                            mergeAlias,
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

                    ExecutionResponse(
                        ExecutionResult.SUCCESS_STARTING_ONLY,
                        mapOf(loginUserOp.onesignalId to backendOneSignalId),
                        metadata = LoginWaitMetadata(backendOneSignalId),
                    )
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
                else -> ExecutionResponse(result.result, retryAfterSeconds = result.retryAfterSeconds)
            }
        }
    }

    // NUL cannot be stored as an alias. Leave it on the op so the user binding stays intact.
    private fun resolveAlias(loginUserOp: LoginUserOperation): String? {
        val rawExternalId = loginUserOp.externalId
        if (rawExternalId == null || !hasNullByte(rawExternalId)) return rawExternalId
        Logging.error("login: externalId contains a null byte; dropping the alias")
        _identityModelStore.model.clearExternalIdIf(rawExternalId, loginUserOp.onesignalId)
        return null
    }

    private suspend fun createUser(
        createUserOperation: LoginUserOperation,
        operations: List<Operation>,
        mergeIntoOnesignalId: String? = null,
    ): ExecutionResponse {
        val identities = mutableMapOf<String, String>()
        mergeIntoOnesignalId?.let { identities[IdentityConstants.ONESIGNAL_ID] = it }
        var subscriptions = mapOf<String, SubscriptionObject>()
        val properties =
            PropertiesObject(
                tags = createUserOperation.tags.takeIf { it.isNotEmpty() },
                language = _languageContext.language,
                timezoneId = TimeUtils.getTimeZoneId(),
            )

        createUserOperation.externalId?.takeUnless { hasNullByte(it) }?.let {
            identities[IdentityConstants.EXTERNAL_ID] = it
        }
        for ((label, id) in createUserOperation.aliases) {
            if (reservedLoginAliasLabel(label) || hasNullByte(label) || hasNullByte(id)) {
                Logging.warn("LoginUserOperationExecutor: skipping reserved or invalid alias label")
                continue
            }
            identities[label] = id
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
        subscriptions =
            LoginProfileApplier.addSubscriptions(createUserOperation, subscriptions) { type, token, grouped ->
                profileSubscription(type, token, profileEnabled(type, createUserOperation), grouped)
            }

        try {
            val subscriptionList = subscriptions.toList()
            val jwt = resolveJwt(createUserOperation, _jwtTokenStore, _identityVerificationService)
            val response =
                _userBackend.createUser(createUserOperation.appId, identities, subscriptionList.map { it.second }, properties, jwt)
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

            val backendSubscriptions = response.subscriptions.toMutableSet()
            var emailSubscriptionId: String? = null
            var smsSubscriptionId: String? = null

            for (pair in subscriptionList) {
                val backendSubscription = matchingBackendSubscription(backendSubscriptions, pair.first, pair.second)

                if (backendSubscription != null) {
                    if (!LoginProfileApplier.isProfileSubscriptionKey(pair.first)) {
                        idTranslations[pair.first] = backendSubscription.id!!
                    }

                    if (_configModelStore.model.pushSubscriptionId == pair.first) {
                        _configModelStore.model.pushSubscriptionId = backendSubscription.id
                    }

                    val subscriptionModel = _subscriptionsModelStore.get(pair.first)
                    if (subscriptionModel != null) {
                        subscriptionModel.setStringProperty(SubscriptionModel::id.name, backendSubscription.id!!, ModelChangeTags.HYDRATE)
                        // The profile status overrode this grouped operation on the wire, so the local record follows.
                        profileStatusForAddress(backendSubscription.type, backendSubscription.token, createUserOperation)?.let {
                            LoginProfileApplier.applyStatus(subscriptionModel, it)
                        }
                    } else if (LoginProfileApplier.isProfileSubscriptionKey(pair.first)) {
                        if (_identityModelStore.model.onesignalId == backendOneSignalId) {
                            LoginProfileApplier.persistSubscription(
                                backendSubscription,
                                LoginProfileApplier.fallbackToken(backendSubscription.type, createUserOperation),
                                _subscriptionsModelStore,
                                optedIn = profileEnabled(backendSubscription.type, createUserOperation),
                            )
                        }
                        when (LoginProfileApplier.subscriptionType(backendSubscription.type)) {
                            SubscriptionType.EMAIL -> emailSubscriptionId = backendSubscription.id
                            SubscriptionType.SMS -> smsSubscriptionId = backendSubscription.id
                            else -> {}
                        }
                    } else {
                        Logging.error("LoginUserOperationExecutor.createUser response is missing a local subscription model for ${pair.first}")
                    }
                } else if (!LoginProfileApplier.isProfileSubscriptionKey(pair.first)) {
                    Logging.error("LoginUserOperationExecutor.createUser response is missing subscription data for ${pair.first}")
                }

                // Remove the processed backend subscription
                backendSubscriptions.remove(backendSubscription)
            }

            // Forward the create-user RYW data to ConsistencyManager so InAppMessagesManager
            // can fetch IAMs once the user record has propagated. Gated on newCodePathsRun:
            // Phase 1 users don't await RYW (legacy IAM fetch path), so storing it would be
            // a no-op anyway. Backend only sends rywData under IV.
            if (_identityVerificationService.newCodePathsRun) {
                response.rywData?.let { rywData ->
                    _consistencyManager.setRywData(backendOneSignalId, IamFetchRywTokenKey.USER, rywData)
                }
            }

            if (_identityModelStore.model.onesignalId == backendOneSignalId) {
                LoginProfileApplier.hydrate(createUserOperation, response, _identityModelStore, _propertiesModelStore)
            } else if (createUserOperation.hasProfileFields()) {
                Logging.warn("LoginUserOperationExecutor: skipped profile hydration because the current identity is not the created user")
            }

            val wasPossiblyAnUpsert = identities.isNotEmpty()
            val followUpOperations =
                if (wasPossiblyAnUpsert) {
                    listOf(RefreshUserOperation(createUserOperation.appId, backendOneSignalId, createUserOperation.externalId))
                } else {
                    null
                }

            return ExecutionResponse(
                ExecutionResult.SUCCESS,
                idTranslations,
                followUpOperations,
                metadata = LoginWaitMetadata(backendOneSignalId, emailSubscriptionId, smsSubscriptionId),
            )
        } catch (ex: BackendException) {
            val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)
            val response =
                when (responseType) {
                    NetworkUtils.ResponseStatusType.RETRYABLE ->
                        backendExecutionResponse(ExecutionResult.FAIL_RETRY, ex)
                    NetworkUtils.ResponseStatusType.UNAUTHORIZED ->
                        backendExecutionResponse(ExecutionResult.FAIL_UNAUTHORIZED, ex)
                    else ->
                        backendExecutionResponse(ExecutionResult.FAIL_NORETRY, ex)
                }
            // The caller retries an abandoned merge, so this failure is not the outcome.
            if (mergeIntoOnesignalId != null && shouldAbandonMerge(response)) {
                Logging.debug("LoginUserOperationExecutor merge attempt failed: HTTP ${ex.statusCode}")
                if (responseType == NetworkUtils.ResponseStatusType.CONFLICT) {
                    // Hardcode user-1. The 409 line above reads like a failure to the app.
                    Logging.debug(
                        "LoginUserOperationExecutor now handling 409 response with \"code\": \"user-1\" by switching to user with \"external_id\": \"${createUserOperation.externalId}\"",
                    )
                }
            } else {
                Logging.error("LoginUserOperationExecutor.createUser failed: HTTP ${ex.statusCode}")
            }
            return response
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
        val subId = if (!IDManager.isLocalId(operation.subscriptionId)) operation.subscriptionId else null
        mutableSubscriptions[operation.subscriptionId] =
            subscriptionWithDevice(
                subscriptionType,
                operation.address,
                operation.enabled,
                operation.status.value,
                subId,
            )

        return mutableSubscriptions
    }

    // Create User applies a status only when one is sent, so omitting leaves an address
    // that already exists alone. The caller states intent; the SDK never assumes it.
    private fun profileSubscription(
        type: SubscriptionObjectType,
        token: String,
        enabled: Boolean?,
        grouped: SubscriptionObject?,
    ): SubscriptionObject? {
        // A grouped addEmail/addSms already carries a subscribed status. Only an explicit
        // profile status may override it, otherwise that operation is left untouched.
        if (grouped != null) {
            if (enabled == null) return null
            Logging.debug("LoginUserOperationExecutor: profile status $enabled overrides a grouped $type subscription")
            return LoginProfileApplier.withStatus(grouped, enabled)
        }
        return subscriptionWithDevice(
            type,
            token,
            enabled = enabled,
            notificationTypes =
            when (enabled) {
                true -> SubscriptionStatus.SUBSCRIBED.value
                false -> SubscriptionStatus.UNSUBSCRIBE.value
                null -> null
            },
        )
    }

    private fun profileEnabled(
        type: SubscriptionObjectType?,
        operation: LoginUserOperation,
    ): Boolean? =
        when (LoginProfileApplier.subscriptionType(type)) {
            SubscriptionType.EMAIL -> operation.emailEnabled
            SubscriptionType.SMS -> operation.smsEnabled
            else -> null
        }

    /** A status only applies to the address the caller stated it for, never a sibling of the same type. */
    private fun profileStatusForAddress(
        type: SubscriptionObjectType?,
        address: String?,
        operation: LoginUserOperation,
    ): Boolean? {
        val profileAddress =
            when (LoginProfileApplier.subscriptionType(type)) {
                SubscriptionType.EMAIL -> operation.email
                SubscriptionType.SMS -> operation.sms
                else -> null
            } ?: return null
        val matches = sameSubscriptionAddress(profileAddress, address, isEmail = type == SubscriptionObjectType.EMAIL)
        return if (matches) profileEnabled(type, operation) else null
    }

    private fun subscriptionWithDevice(
        type: SubscriptionObjectType,
        token: String,
        enabled: Boolean? = null,
        notificationTypes: Int? = null,
        id: String? = null,
    ) = SubscriptionObject(
        id = id,
        type = type,
        token = token,
        enabled = enabled,
        notificationTypes = notificationTypes,
        sdk = OneSignalUtils.sdkVersion,
        deviceModel = Build.MODEL,
        deviceOS = Build.VERSION.RELEASE,
        rooted = RootToolsInternalMethods.isRooted,
        netType = DeviceUtils.getNetType(_application.appContext),
        carrier = DeviceUtils.getCarrierName(_application.appContext),
        appVersion = AndroidUtils.getAppVersion(_application.appContext),
    )

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

    private fun shouldCreateUserDirectly(op: LoginUserOperation, alias: String?): Boolean {
        if (op.hasProfileFields() || _identityVerificationService.ivBehaviorActive) return true
        return op.existingOnesignalId == null || alias == null
    }

    // A profile login sends the anonymous user's ID with the new external ID, so Create User attaches
    // the external ID to that user instead of creating a new one, like the SetAlias path does.
    private fun anonymousUserToMerge(op: LoginUserOperation, alias: String?): String? {
        if (!op.hasProfileFields() || _identityVerificationService.ivBehaviorActive || alias == null) return null
        return op.existingOnesignalId?.takeUnless { IDManager.isLocalId(it) }
    }

    // Drop the anonymous id only when it can be why Create User failed.
    // A 400 is the payload, so a second request would fail the same way.
    private fun shouldAbandonMerge(response: ExecutionResponse): Boolean {
        if (response.result != ExecutionResult.FAIL_NORETRY) return false
        val status = response.httpStatusCode?.let { NetworkUtils.getResponseStatusType(it) }
        return status == NetworkUtils.ResponseStatusType.MISSING ||
            (status == NetworkUtils.ResponseStatusType.CONFLICT && onesignalIdConflict(response.httpResponse) != false)
    }

    /** False when a parsed error does not name onesignal_id. Null when the body has none. */
    private fun onesignalIdConflict(body: String?): Boolean? =
        OneSignalError.fromBackendResponse(HTTP_CONFLICT, body)
            .details
            .filter { it.backendCode != null || conflictingAliases(it).isNotEmpty() }
            .takeIf { it.isNotEmpty() }
            ?.any { namesMergedUser(it) }

    // user-1 is any alias clash. Retry only when conflicting_aliases names onesignal_id.
    private fun namesMergedUser(detail: OneSignalError.Detail): Boolean =
        conflictingAliases(detail).containsKey(IdentityConstants.ONESIGNAL_ID)

    private fun conflictingAliases(detail: OneSignalError.Detail): Map<*, *> =
        detail.meta?.get(CONFLICTING_ALIASES) as? Map<*, *> ?: emptyMap<String, Any?>()

    private fun backendExecutionResponse(
        result: ExecutionResult,
        ex: BackendException,
    ) = ExecutionResponse(
        result,
        retryAfterSeconds = ex.retryAfterSeconds,
        httpStatusCode = ex.statusCode,
        httpResponse = ex.response,
    )

    companion object {
        const val LOGIN_USER = "login-user"
        private const val HTTP_CONFLICT = 409
        private const val CONFLICTING_ALIASES = "conflicting_aliases"
    }
}

// Find the corresponding subscription (subscriptions are not returned in the order they are sent)
internal fun matchingBackendSubscription(
    remaining: Set<SubscriptionObject>,
    localId: String,
    local: SubscriptionObject,
): SubscriptionObject? =
    remaining.firstOrNull { it.id == localId }
        ?: matchByToken(remaining, local)
        ?: matchByType(remaining, local)

// Should always succeed for email or sms once the ID match fails.
private fun matchByToken(
    remaining: Set<SubscriptionObject>,
    local: SubscriptionObject,
): SubscriptionObject? {
    val localToken = local.token
    if (localToken.isNullOrBlank()) return null
    val ignoreCase = local.type == SubscriptionObjectType.EMAIL
    val hashed = PIIHasher.hash(localToken)
    return remaining.firstOrNull { backend ->
        val token = backend.token
        !token.isNullOrBlank() && (token.equals(localToken, ignoreCase) || token == hashed)
    }
}

// Push: at most one remains. Email/SMS: only if one remains and it has no token.
private fun matchByType(
    remaining: Set<SubscriptionObject>,
    local: SubscriptionObject,
): SubscriptionObject? {
    val sameType = remaining.filter { it.type == local.type }
    val isEmailOrSms = local.type == SubscriptionObjectType.EMAIL || local.type == SubscriptionObjectType.SMS
    return if (isEmailOrSms) sameType.singleOrNull()?.takeIf { it.token.isNullOrBlank() } else sameType.firstOrNull()
}
