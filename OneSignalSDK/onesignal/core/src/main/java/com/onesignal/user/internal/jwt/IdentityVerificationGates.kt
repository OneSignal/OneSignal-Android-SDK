package com.onesignal.user.internal.jwt

import com.onesignal.debug.internal.logging.Logging

/**
 * Current Identity Verification gate state, pushed by `FeatureManager.applySideEffects`.
 *
 * Stored state is the tri-state [jwtRequirement] — UNKNOWN until the first HYDRATE so consumers
 * that need to distinguish "we don't know yet" from "customer opted out" can. The Boolean
 * derivations [ivBehaviorActive] and [newCodePathsRun] cover the common case where consumers
 * just want yes/no; UNKNOWN reads as `false` for both, which is the safe default before HYDRATE.
 *
 * The two gates differ on purpose: [newCodePathsRun] also flips on when our SDK feature flag is
 * on, honoring rollout state even when the customer hasn't opted in. [ivBehaviorActive] tracks
 * customer config alone.
 *
 * Invariant `ivBehaviorActive == true ⇒ newCodePathsRun == true` is preserved at every
 * observation because both are derived on read from the stored inputs; a reader can't observe an
 * inconsistent state.
 */
internal object IdentityVerificationGates {
    @Volatile
    private var _featureFlagOn: Boolean = false

    /** Customer config (`jwt_required`); [JwtRequirement.UNKNOWN] until the first HYDRATE. */
    @Volatile
    var jwtRequirement: JwtRequirement = JwtRequirement.UNKNOWN
        private set

    /** Whether IV-specific behavior (JWT attachment, auth error handling) applies. UNKNOWN reads as `false`. */
    val ivBehaviorActive: Boolean
        get() = jwtRequirement == JwtRequirement.REQUIRED

    /** Whether new IV-related code paths should run. `featureFlag_IV_ON || jwt_required == REQUIRED`. */
    val newCodePathsRun: Boolean
        get() = _featureFlagOn || ivBehaviorActive

    /** Idempotent. [source] is logged for traceability. */
    fun update(
        featureFlagOn: Boolean,
        jwtRequirement: JwtRequirement,
        source: String,
    ) {
        _featureFlagOn = featureFlagOn
        this.jwtRequirement = jwtRequirement

        Logging.debug(
            "OneSignal: IdentityVerificationGates.update: newCodePathsRun=$newCodePathsRun, " +
                "ivBehaviorActive=$ivBehaviorActive (source=$source)",
        )
    }
}
