package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.common.NetworkUtils
import com.onesignal.common.TimeUtils
import com.onesignal.common.exceptions.BackendException
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.config.impl.IdentityVerificationService
import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.ExecutionResult
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.LogLevel
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.SubscriptionObject
import com.onesignal.user.internal.backend.SubscriptionObjectType
import com.onesignal.user.internal.builduser.IRebuildUserService
import com.onesignal.user.internal.identity.IdentityModel
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.jwt.JwtTokenStore
import com.onesignal.user.internal.operations.RefreshUserOperation
import com.onesignal.user.internal.operations.UpdateSubscriptionOperation
import com.onesignal.user.internal.operations.impl.listeners.SubscriptionModelStoreListener
import com.onesignal.user.internal.operations.impl.states.NewRecordsState
import com.onesignal.user.internal.properties.PropertiesModel
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionModel
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.SubscriptionStatus
import com.onesignal.user.internal.subscriptions.SubscriptionType

internal class RefreshUserOperationExecutor(
    private val _userBackend: IUserBackendService,
    private val _identityModelStore: IdentityModelStore,
    private val _propertiesModelStore: PropertiesModelStore,
    private val _subscriptionsModelStore: SubscriptionModelStore,
    private val _configModelStore: ConfigModelStore,
    private val _buildUserService: IRebuildUserService,
    private val _newRecordState: NewRecordsState,
    private val _jwtTokenStore: JwtTokenStore,
    private val _identityVerificationService: IdentityVerificationService,
) : IOperationExecutor {
    override val operations: List<String>
        get() = listOf(REFRESH_USER)

    override suspend fun execute(operations: List<Operation>): ExecutionResponse {
        Logging.log(LogLevel.DEBUG, "RefreshUserOperationExecutor(operation: $operations)")

        if (operations.any { it !is RefreshUserOperation }) {
            throw Exception("Unrecognized operation(s)! Attempted operations:\n$operations")
        }

        val startingOp = operations.first()
        if (startingOp is RefreshUserOperation) {
            return getUser(startingOp)
        }

        throw Exception("Unrecognized operation: $startingOp")
    }

    private suspend fun getUser(op: RefreshUserOperation): ExecutionResponse {
        val params = resolveBackendParams(op, op.onesignalId, _jwtTokenStore, _identityVerificationService)
        try {
            val response =
                _userBackend.getUser(
                    op.appId,
                    params.aliasLabel,
                    params.aliasValue,
                    params.jwt,
                )

            if (op.onesignalId != _identityModelStore.model.onesignalId) {
                return ExecutionResponse(ExecutionResult.SUCCESS)
            }

            val identityModel = IdentityModel()
            for (aliasKVP in response.identities) {
                identityModel[aliasKVP.key] = aliasKVP.value
            }

            val propertiesModel = PropertiesModel()
            propertiesModel.onesignalId = op.onesignalId

            if (response.properties.country != null) {
                propertiesModel.country = response.properties.country
            }

            if (response.properties.language != null) {
                propertiesModel.language = response.properties.language
            }

            if (response.properties.tags != null) {
                for (tagKVP in response.properties.tags) {
                    if (tagKVP.value != null) {
                        propertiesModel.tags[tagKVP.key] = tagKVP.value!!
                    }
                }
            }

            // No longer hydrate timezone from remote, set locally
            propertiesModel.timezone = TimeUtils.getTimeZoneId()

            // Retrieve the push subscription ID from the backend configuration model store. Used
            // both for re-attaching the locally-cached push model below and for the SDK-4474
            // self-heal divergence check inside the loop.
            val pushSubscriptionIdFromConfig = _configModelStore.model.pushSubscriptionId

            val subscriptionModels = mutableListOf<SubscriptionModel>()
            var pushSelfHealOperation: UpdateSubscriptionOperation? = null
            for (subscription in response.subscriptions) {
                val subscriptionModel = SubscriptionModel()
                subscriptionModel.id = subscription.id!!
                subscriptionModel.address = subscription.token ?: ""
                subscriptionModel.status = SubscriptionStatus.fromInt(subscription.notificationTypes ?: SubscriptionStatus.SUBSCRIBED.value) ?: SubscriptionStatus.SUBSCRIBED
                subscriptionModel.type =
                    when (subscription.type!!) {
                        SubscriptionObjectType.EMAIL -> {
                            SubscriptionType.EMAIL
                        }
                        SubscriptionObjectType.SMS -> {
                            SubscriptionType.SMS
                        }
                        else -> {
                            SubscriptionType.PUSH
                        }
                    }
                subscriptionModel.optedIn = subscriptionModel.status != SubscriptionStatus.UNSUBSCRIBE && subscriptionModel.status != SubscriptionStatus.DISABLED_FROM_REST_API_DEFAULT_REASON
                subscriptionModel.sdk = subscription.sdk ?: ""
                subscriptionModel.deviceOS = subscription.deviceOS ?: ""
                subscriptionModel.carrier = subscription.carrier ?: ""
                subscriptionModel.appVersion = subscription.appVersion ?: ""

                // We only add a non-push subscriptions. For push, the device is the source of truth
                // so we don't want to cache these subscriptions from the backend.
                if (subscriptionModel.type != SubscriptionType.PUSH) {
                    subscriptionModels.add(subscriptionModel)
                } else if (subscription.id == pushSubscriptionIdFromConfig && pushSelfHealOperation == null) {
                    // SDK-4474 self-heal for SDK-4388 victims. The buggy
                    // SubscriptionOperationExecutor in older SDK builds dispatched the merged
                    // create-subscription + update-subscription(SUBSCRIBED) batch as a POST
                    // /subscriptions carrying the already-existing server-side id; the server
                    // responded 200 {} (no-op) and the local op queue was emptied, leaving the
                    // server stuck at enabled:false / notification_types:0. Once that user
                    // upgrades to a fixed SDK build, nothing in the queue triggers the fix path.
                    //
                    // Detect the divergence here (every session start runs RefreshUser) and
                    // re-assert local truth via PATCH. "Device is the source of truth" is the
                    // existing policy for push; this just enforces it across the wire when the
                    // server has drifted out of sync.
                    pushSelfHealOperation = buildPushSelfHealOperationForStuckSubscription(op, subscription, pushSubscriptionIdFromConfig)
                }
            }

            if (pushSubscriptionIdFromConfig != null) {
                // Retrieve the push subscription model from the model store
                val cachedPushSubscriptionModel = _subscriptionsModelStore.get(pushSubscriptionIdFromConfig)

                // If non-null, the cached push subscription matches the ID coming from backend config
                if (cachedPushSubscriptionModel != null) {
                    subscriptionModels.add(cachedPushSubscriptionModel)
                }
            }

            _identityModelStore.replace(identityModel, ModelChangeTags.HYDRATE)
            _propertiesModelStore.replace(propertiesModel, ModelChangeTags.HYDRATE)
            _subscriptionsModelStore.replaceAll(subscriptionModels, ModelChangeTags.HYDRATE)

            return ExecutionResponse(
                ExecutionResult.SUCCESS,
                operations = pushSelfHealOperation?.let { listOf<Operation>(it) },
            )
        } catch (ex: BackendException) {
            val responseType = NetworkUtils.getResponseStatusType(ex.statusCode)

            return when (responseType) {
                NetworkUtils.ResponseStatusType.RETRYABLE ->
                    ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                NetworkUtils.ResponseStatusType.UNAUTHORIZED ->
                    ExecutionResponse(ExecutionResult.FAIL_UNAUTHORIZED, retryAfterSeconds = ex.retryAfterSeconds)
                NetworkUtils.ResponseStatusType.MISSING -> {
                    if (ex.statusCode == 404 && _newRecordState.isInMissingRetryWindow(op.onesignalId)) {
                        return ExecutionResponse(ExecutionResult.FAIL_RETRY, retryAfterSeconds = ex.retryAfterSeconds)
                    }
                    val operations = _buildUserService.getRebuildOperationsIfCurrentUser(op.appId, op.onesignalId)
                    return if (operations == null) {
                        ExecutionResponse(ExecutionResult.FAIL_NORETRY)
                    } else {
                        ExecutionResponse(
                            ExecutionResult.FAIL_RETRY,
                            operations = operations,
                            retryAfterSeconds = ex.retryAfterSeconds,
                        )
                    }
                }
                else ->
                    ExecutionResponse(ExecutionResult.FAIL_NORETRY)
            }
        }
    }

    /**
     * SDK-4474 self-heal builder. Returns an [UpdateSubscriptionOperation] iff the cached
     * push subscription model resolves to enabled-and-opted-in but the server response
     * recorded the same id as disabled. Returns null otherwise (no divergence — no-op).
     *
     * Caller has already verified that [serverSubscription.id] matches the cached
     * `pushSubscriptionId`, so this method only inspects the local model + server flags.
     */
    @Suppress("ReturnCount")
    private fun buildPushSelfHealOperationForStuckSubscription(
        op: RefreshUserOperation,
        serverSubscription: SubscriptionObject,
        pushSubscriptionId: String,
    ): UpdateSubscriptionOperation? {
        val cachedPushSubscriptionModel = _subscriptionsModelStore.get(pushSubscriptionId)
        if (cachedPushSubscriptionModel == null || cachedPushSubscriptionModel.type != SubscriptionType.PUSH) {
            return null
        }

        val (localEnabled, localStatus) = SubscriptionModelStoreListener.getSubscriptionEnabledAndStatus(cachedPushSubscriptionModel)
        val serverEnabled = (serverSubscription.enabled == true) && ((serverSubscription.notificationTypes ?: 0) > 0)
        if (!localEnabled || serverEnabled) {
            return null
        }

        Logging.info(
            "RefreshUserOperationExecutor: push subscription $pushSubscriptionId diverged from server " +
                "(server enabled=${serverSubscription.enabled} notificationTypes=${serverSubscription.notificationTypes}; " +
                "local opted-in and SUBSCRIBED). Enqueuing follow-up update-subscription op to re-assert local " +
                "truth via PATCH /subscriptions/{id}. See SDK-4388 / SDK-4474.",
        )

        return UpdateSubscriptionOperation(
            op.appId,
            op.onesignalId,
            _identityModelStore.model.externalId,
            pushSubscriptionId,
            cachedPushSubscriptionModel.type,
            localEnabled,
            cachedPushSubscriptionModel.address,
            localStatus,
        )
    }

    companion object {
        const val REFRESH_USER = "refresh-user"
    }
}
