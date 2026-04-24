package com.onesignal.user.internal.jwt

/**
 * Customer-side JWT requirement, mirrored from the backend `jwt_required` remote param.
 * Explicit [UNKNOWN] so callers can distinguish pre-HYDRATE (no value yet) from
 * [NOT_REQUIRED] (customer opted out).
 *
 * Represents only the customer-config side of Identity Verification; do not confuse
 * with [com.onesignal.core.internal.features.FeatureFlag.IDENTITY_VERIFICATION] (our
 * SDK-side rollout switch).
 */
internal enum class JwtRequirement {
    /** Remote params have not been fetched yet. Treat as non-IV until known. */
    UNKNOWN,

    /** Customer config `jwt_required=false`. No JWT signing. */
    NOT_REQUIRED,

    /** Customer config `jwt_required=true`. IV-specific behavior active. */
    REQUIRED,
    ;

    companion object {
        fun fromBoolean(value: Boolean?): JwtRequirement =
            when (value) {
                null -> UNKNOWN
                false -> NOT_REQUIRED
                true -> REQUIRED
            }
    }
}
