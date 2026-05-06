package com.onesignal.user.internal.operations.impl.executors

import com.onesignal.core.internal.config.impl.IdentityVerificationService
import com.onesignal.core.internal.operations.Operation
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.backend.IdentityConstants
import com.onesignal.user.internal.jwt.JwtTokenStore

/**
 * IV-specific parameter resolution for operation executors. Base-class call sites dispatch via
 * `if (newCodePathsRun) resolveIvBackendParams(...) else IvBackendParams.legacyFor(...)`;
 * the inner `ivBehaviorActive` check keeps Phase 3 users (new code path on, IV behavior off)
 * on legacy alias/jwt values so they exercise the dispatch without any behavioral change.
 *
 * `ivBehaviorActive` is passed in by the executor (read from the injected
 * `IdentityVerificationService`) — extension functions are not classes and cannot inject.
 */

/** Alias + JWT triplet passed into backend calls. */
internal data class IvBackendParams(
    val aliasLabel: String,
    val aliasValue: String,
    val jwt: String?,
) {
    companion object {
        /** Values used when the new code path is off or IV behavior is inactive. Byte-for-byte identical to pre-IV behavior. */
        fun legacyFor(onesignalId: String) = IvBackendParams(IdentityConstants.ONESIGNAL_ID, onesignalId, null)
    }
}

/**
 * Combined outer + inner gate for executors that just want "the right alias/JWT for this op."
 * Replaces the call-site `if (newCodePathsRun) resolveIvBackendParams(...) else legacyFor(...)`
 * boilerplate. Phase 1 (newCodePathsRun=false) skips the new code path entirely and returns
 * legacy values directly, preserving the rollout-safety property that Phase 1 traffic does not
 * exercise the IV resolution code.
 */
internal fun resolveBackendParams(
    op: Operation,
    onesignalId: String,
    jwtTokenStore: JwtTokenStore,
    identityVerificationService: IdentityVerificationService,
): IvBackendParams =
    if (identityVerificationService.newCodePathsRun) {
        resolveIvBackendParams(op, onesignalId, jwtTokenStore, identityVerificationService.ivBehaviorActive)
    } else {
        IvBackendParams.legacyFor(onesignalId)
    }

/**
 * Resolves alias + JWT for a backend call. Intended to be called only when
 * `newCodePathsRun` is true; base-class dispatch handles the outer gate. Most executors should
 * call [resolveBackendParams] which encapsulates the outer gate.
 *
 * - IV behavior inactive (Phase 3) → legacy values; no alias switch, no JWT attach.
 * - IV behavior active + op has externalId → `external_id` alias + JWT from [JwtTokenStore].
 *   Note: `hasValidJwtIfRequired` already keeps anon ops and missing-JWT ops out of dispatch while
 *   IV is active, so the null-externalId / missing-JWT branches below are defensive only.
 *
 * [onesignalId] is passed explicitly because the base [Operation] class doesn't expose it;
 * concrete executors all have their own onesignalId field on the operation they're about to send.
 */
internal fun resolveIvBackendParams(
    op: Operation,
    onesignalId: String,
    jwtTokenStore: JwtTokenStore,
    ivBehaviorActive: Boolean,
): IvBackendParams {
    if (!ivBehaviorActive) return IvBackendParams.legacyFor(onesignalId)
    val externalId = op.externalId
    if (externalId == null) {
        Logging.error("IV active but op has null externalId; falling back to onesignal_id")
        return IvBackendParams.legacyFor(onesignalId)
    }
    return IvBackendParams(IdentityConstants.EXTERNAL_ID, externalId, jwtTokenStore.getJwt(externalId))
}

/**
 * Combined outer + inner gate for executors that need only a JWT (no alias switch). Replaces
 * the call-site `if (newCodePathsRun) resolveIvJwt(...) else null` boilerplate. Phase 1
 * (newCodePathsRun=false) skips the new code path entirely and returns null directly,
 * preserving the rollout-safety property that Phase 1 traffic does not exercise IV resolution.
 */
internal fun resolveJwt(
    op: Operation,
    jwtTokenStore: JwtTokenStore,
    identityVerificationService: IdentityVerificationService,
): String? =
    if (identityVerificationService.newCodePathsRun) {
        resolveIvJwt(op, jwtTokenStore, identityVerificationService.ivBehaviorActive)
    } else {
        null
    }

/**
 * Resolves a JWT-only parameter (used by endpoints that don't take alias label/value, e.g.
 * subscription update/delete, custom events). Same inner gating as [resolveIvBackendParams].
 * Intended to be called only when `newCodePathsRun` is true; most executors should call
 * [resolveJwt] which encapsulates the outer gate.
 */
internal fun resolveIvJwt(
    op: Operation,
    jwtTokenStore: JwtTokenStore,
    ivBehaviorActive: Boolean,
): String? {
    if (!ivBehaviorActive) return null
    val externalId = op.externalId ?: return null
    return jwtTokenStore.getJwt(externalId)
}

/**
 * LoginUserFromSubscription path is blocked under IV — the backend endpoint the v4→v5 upgrade path
 * relies on is not allowed when `jwt_required == true`. Returns `true` when the executor should
 * short-circuit to `FAIL_NORETRY`. Called only when `newCodePathsRun` is true; Phase 3 users
 * (behavior inactive) fall through and use the legacy migration path.
 */
internal fun shouldFailLoginUserFromSubscription(ivBehaviorActive: Boolean): Boolean = ivBehaviorActive
