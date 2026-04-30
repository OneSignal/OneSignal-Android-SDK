package com.onesignal.core.internal.config.impl

import com.onesignal.common.modeling.ISingletonModelStoreChangeHandler
import com.onesignal.common.modeling.ModelChangeTags
import com.onesignal.common.modeling.ModelChangedArgs
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.features.FeatureFlag
import com.onesignal.core.internal.features.IFeatureManager
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.user.internal.jwt.JwtRequirement

/**
 * Single source of truth for Identity Verification gating, and for forwarding HYDRATE events to
 * the [com.onesignal.core.internal.operations.IOperationRepo] post-HYDRATE choreography.
 *
 * Gate state is derived on read from the injected [IFeatureManager] (rollout flag) and
 * [ConfigModelStore] (customer `jwt_required`); nothing is duplicated here. UNKNOWN
 * (pre-HYDRATE) reads as `false` for both gates, which is the safe default.
 *
 * Invariant `ivBehaviorActive == true ⇒ newCodePathsRun == true` holds because both are derived
 * from the same `useIdentityVerification` field.
 *
 * Consumers (e.g. OperationRepo) wire post-HYDRATE behavior via [setOnJwtConfigHydratedHandler];
 * the handler fires once per HYDRATE with `ivRequired = useIdentityVerification == REQUIRED`.
 */
internal class IdentityVerificationService(
    private val featureManager: IFeatureManager,
    private val configModelStore: ConfigModelStore,
) : IStartableService, ISingletonModelStoreChangeHandler<ConfigModel> {
    /** Whether IV-specific behavior (JWT attachment, auth error handling) applies. UNKNOWN reads as `false`. */
    val ivBehaviorActive: Boolean
        get() = configModelStore.model.useIdentityVerification == JwtRequirement.REQUIRED

    /** Whether new IV-related code paths should run. `featureFlag_IV_ON || jwt_required == REQUIRED`. */
    val newCodePathsRun: Boolean
        get() = featureManager.isEnabled(FeatureFlag.SDK_IDENTITY_VERIFICATION) || ivBehaviorActive

    @Volatile
    private var _onJwtConfigHydrated: ((ivRequired: Boolean) -> Unit)? = null

    /**
     * Register a handler invoked once per HYDRATE of the config model. Used by OperationRepo to
     * release pre-HYDRATE deferral and (when IV is required) purge anonymous queued ops.
     * Pass `null` to clear.
     */
    fun setOnJwtConfigHydratedHandler(handler: ((ivRequired: Boolean) -> Unit)?) {
        _onJwtConfigHydrated = handler
    }

    override fun start() {
        configModelStore.subscribe(this)
    }

    override fun onModelReplaced(
        model: ConfigModel,
        tag: String,
    ) {
        if (tag != ModelChangeTags.HYDRATE) return
        _onJwtConfigHydrated?.invoke(model.useIdentityVerification == JwtRequirement.REQUIRED)
    }

    override fun onModelUpdated(
        args: ModelChangedArgs,
        tag: String,
    ) {
        // Remote params arrive as full-model replacements (HYDRATE); individual property
        // updates are not expected for useIdentityVerification.
    }
}
