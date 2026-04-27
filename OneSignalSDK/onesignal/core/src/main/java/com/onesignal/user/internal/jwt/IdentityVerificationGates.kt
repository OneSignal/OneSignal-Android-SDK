package com.onesignal.user.internal.jwt

import com.onesignal.debug.internal.logging.Logging

/**
 * Current Identity Verification gate state, pushed by `FeatureManager.applySideEffects`.
 *
 * The two gates differ on purpose: [newCodePathsRun] also flips on when customer config
 * (`jwt_required`) is true — honoring customer setup even if our feature flag is off.
 * [ivBehaviorActive] tracks customer config alone.
 *
 * Invariant `ivBehaviorActive == true ⇒ newCodePathsRun == true` is preserved at every
 * observation because [newCodePathsRun] is derived on read from the stored inputs; a reader
 * can't observe a state where one field is `true` and the other is `false` inconsistent
 * with the formula.
 */
internal object IdentityVerificationGates {
    @Volatile
    private var _featureFlagOn: Boolean = false

    /** Whether IV-specific behavior (JWT attachment, auth error handling) applies. `jwt_required == true`. */
    @Volatile
    var ivBehaviorActive: Boolean = false
        private set

    /** Whether new IV-related code paths should run. `featureFlag_IV_ON || jwt_required == true`. */
    val newCodePathsRun: Boolean
        get() = _featureFlagOn || ivBehaviorActive

    /** Idempotent. [source] is logged for traceability. */
    fun update(
        featureFlagOn: Boolean,
        jwtRequirement: JwtRequirement,
        source: String,
    ) {
        _featureFlagOn = featureFlagOn
        ivBehaviorActive = jwtRequirement == JwtRequirement.REQUIRED

        Logging.debug(
            "OneSignal: IdentityVerificationGates.update: newCodePathsRun=$newCodePathsRun, " +
                "ivBehaviorActive=$ivBehaviorActive (source=$source)",
        )
    }
}
