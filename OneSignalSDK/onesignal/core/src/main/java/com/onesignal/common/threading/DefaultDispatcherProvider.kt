package com.onesignal.common.threading

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job

/**
 * Production implementation of [CoroutineDispatcherProvider] that uses OneSignalDispatchers.
 *
 * This delegates to the existing scopes in OneSignalDispatchers to avoid creating duplicate scopes.
 * The OneSignalDispatchers already maintains IOScope and DefaultScope with SupervisorJob,
 * so we reuse those instead of creating new ones.
 */
class DefaultDispatcherProvider : CoroutineDispatcherProvider {
    override val io: CoroutineDispatcher = OneSignalDispatchers.IO
    override val default: CoroutineDispatcher = OneSignalDispatchers.Default

    override fun launchOnIO(block: suspend () -> Unit): Job {
        // Delegate to OneSignalDispatchers which already has IOScope with SupervisorJob
        return OneSignalDispatchers.launchOnIO(block)
    }

    override fun launchOnDefault(block: suspend () -> Unit): Job {
        // Delegate to OneSignalDispatchers which already has DefaultScope with SupervisorJob
        return OneSignalDispatchers.launchOnDefault(block)
    }
}
