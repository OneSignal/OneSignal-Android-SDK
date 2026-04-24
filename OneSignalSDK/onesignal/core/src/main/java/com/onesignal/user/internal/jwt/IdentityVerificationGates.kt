package com.onesignal.user.internal.jwt

import com.onesignal.debug.internal.logging.Logging

/**
 * Current Identity Verification gate state, pushed by `FeatureManager.applySideEffects`.
 *
 * The two gates differ on purpose: [newCodePathsRun] also flips on when customer config
 * (`jwt_required`) is true — honoring customer setup even if our feature flag is off.
 * [ivBehaviorActive] tracks customer config alone.
 */
internal object IdentityVerificationGates {
    /** Whether new IV-related code paths should run. `featureFlag_IV_ON || jwt_required == true`. */
    @Volatile
    var newCodePathsRun: Boolean = false
        private set

    /** Whether IV-specific behavior (JWT attachment, auth error handling) applies. `jwt_required == true`. */
    @Volatile
    var ivBehaviorActive: Boolean = false
        private set

    /** Idempotent. [source] is logged for traceability when gates change. */
    fun update(
        featureFlagOn: Boolean,
        jwtRequirement: JwtRequirement,
        source: String,
    ) {
        val previousNewCode = newCodePathsRun
        val previousIvActive = ivBehaviorActive

        val required = jwtRequirement == JwtRequirement.REQUIRED
        newCodePathsRun = featureFlagOn || required
        ivBehaviorActive = required

        if (previousNewCode != newCodePathsRun || previousIvActive != ivBehaviorActive) {
            Logging.info(
                "OneSignal: IdentityVerificationGates updated: " +
                    "newCodePathsRun=$newCodePathsRun, ivBehaviorActive=$ivBehaviorActive " +
                    "(source=$source, featureFlagOn=$featureFlagOn, jwtRequirement=$jwtRequirement)",
            )
        } else {
            Logging.debug(
                "OneSignal: IdentityVerificationGates unchanged " +
                    "(source=$source, newCodePathsRun=$newCodePathsRun, ivBehaviorActive=$ivBehaviorActive)",
            )
        }
    }
}
