package com.onesignal.user.internal.migrations

import com.onesignal.common.IDManager
import com.onesignal.common.threading.suspendifyOnIO
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.operations.IOperationRepo
import com.onesignal.core.internal.operations.containsInstanceOf
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.debug.internal.logging.Logging
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.operations.LoginUserOperation

/**
 * Purpose: Automatically recovers a stalled User in the OperationRepo due
 * to a bug in the SDK from 5.0.0 to 5.1.7.
 *
 * Issue: Some calls to OneSignal.login() would not be reflected on the
 * backend and would stall the the queue for that User. This would result
 * in User and Subscription operations to not be processed by
 * OperationRepo.
 * See PR #2046 for more details.
 *
 * Even if the developer called OneSignal.login() again with the same
 * externalId it would not correct the stalled User.
 *   - Only calling logout() or login() with different externalId would
 *     have un-stalled the OperationRepo. And then only after logging
 *     back to the stalled user would it have recover all the unsent
 *     operations they may exist.
 */
class RecoverFromDroppedLoginBug(
    private val _operationRepo: IOperationRepo,
    private val _identityModelStore: IdentityModelStore,
    private val _configModelStore: ConfigModelStore,
) : IStartableService {
    override fun start() {
        suspendifyOnIO {
            _operationRepo.awaitInitialized()
            if (isInBadState()) {
                Logging.warn(
                    "User with externalId:" +
                        "${_identityModelStore.model.externalId} " +
                        "was in a bad state, causing it to not update on OneSignal's " +
                        "backend! We are recovering and replaying all unsent " +
                        "operations now.",
                )
                recoverByAddingBackDroppedLoginOperation()
            }
        }
    }

    // We are in the bad state if ALL are true:
    // 1. externalId is set (because OneSignal.login was called)
    // 2. We don't have a real yet onesignalId
    //   - We haven't made a successful user create call yet.
    // 3. There is no attempt to create the User left in the
    //    OperationRepo's queue.
    private fun isInBadState(): Boolean {
        val externalId = _identityModelStore.model.externalId
        val onesignalId = _identityModelStore.model.onesignalId

        // NOTE: We are not accounting a more rare (and less important case)
        // where a previously logged in User was never created.
        // That is, if another user already logged in successfully, but
        // the last user still has stuck pending operations due to the
        // User never being created on the OneSignal's backend.
        return externalId != null &&
            IDManager.isLocalId(onesignalId) &&
            !_operationRepo.containsInstanceOf<LoginUserOperation>()
    }

    private fun recoverByAddingBackDroppedLoginOperation() {
        // This is the operation that was dropped by mistake in some cases,
        // once it is added to the queue all and it gets executed, all
        // operations waiting on it will be sent.

        // This enqueues at the end, however this is ok, since
        // the OperationRepo is designed find the first operation that is
        // executable.
        _operationRepo.enqueue(
            LoginUserOperation(
                _configModelStore.model.appId,
                _identityModelStore.model.onesignalId,
                _identityModelStore.model.externalId,
                null,
            ),
        )
    }
}
