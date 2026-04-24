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
        jwtRequired: Boolean?,
        source: String,
    ) {
        val previousNewCode = newCodePathsRun
        val previousIvActive = ivBehaviorActive

        newCodePathsRun = featureFlagOn || (jwtRequired == true)
        ivBehaviorActive = jwtRequired == true

        if (previousNewCode != newCodePathsRun || previousIvActive != ivBehaviorActive) {
            Logging.info(
                "OneSignal: IdentityVerificationGates updated: " +
                    "newCodePathsRun=$newCodePathsRun, ivBehaviorActive=$ivBehaviorActive " +
                    "(source=$source, featureFlagOn=$featureFlagOn, jwtRequired=$jwtRequired)",
            )
        } else {
            Logging.debug(
                "OneSignal: IdentityVerificationGates unchanged " +
                    "(source=$source, newCodePathsRun=$newCodePathsRun, ivBehaviorActive=$ivBehaviorActive)",
            )
        }
    }
}
