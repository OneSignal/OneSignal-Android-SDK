package com.onesignal.common.threading

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages all threading for the OneSignal SDK.
 *
 * We use custom thread pools instead of Android's default dispatchers
 * to have better control over thread naming and resource usage.
 */
object OneSignalDispatchers {
          private const val CORE_POOL_SIZE = 2

    private class OneSignalThreadFactory(private val namePrefix: String) : ThreadFactory {
        private val threadNumber = AtomicInteger(1)

        override fun newThread(r: Runnable): Thread {
            val thread = Thread(r, "$namePrefix-${threadNumber.getAndIncrement()}")
            thread.isDaemon = true
            thread.priority = Thread.NORM_PRIORITY
            return thread
        }
    }

    // Thread pools for different types of work
    private val ioExecutor =
        Executors.newFixedThreadPool(
            CORE_POOL_SIZE,
            OneSignalThreadFactory("OneSignal-IO"),
        )

    private val computationExecutor =
        Executors.newFixedThreadPool(
            CORE_POOL_SIZE,
            OneSignalThreadFactory("OneSignal-Computation"),
        )

    // Dispatchers that wrap our thread pools
    val IO: CoroutineDispatcher = ioExecutor.asCoroutineDispatcher()
    val Computation: CoroutineDispatcher = computationExecutor.asCoroutineDispatcher()

    // Scopes for launching coroutines
    val IOScope = CoroutineScope(SupervisorJob() + IO)
    val DefaultScope = CoroutineScope(SupervisorJob() + Computation)

    // Utility functions for common operations
    suspend fun <T> withIO(block: suspend () -> T): T = withContext(IO) { block() }

    suspend fun <T> withComputation(block: suspend () -> T): T = withContext(Computation) { block() }

    fun launchOnIO(block: suspend () -> Unit) {
        IOScope.launch { block() }
    }

    fun launchOnDefault(block: suspend () -> Unit) {
        DefaultScope.launch { block() }
    }

    fun <T> runBlockingOnIO(block: suspend () -> T): T = runBlocking(IO) { block() }

    fun <T> runBlockingOnComputation(block: suspend () -> T): T = runBlocking(Computation) { block() }

    @VisibleForTesting
    fun shutdown() {
        try {
            ioExecutor.shutdown()
            computationExecutor.shutdown()
            IOScope.cancel()
            DefaultScope.cancel()
        } catch (e: Exception) {
            println("Error during OneSignalDispatchers shutdown: ${e.message}")
        }
    }

    fun isInitialized(): Boolean {
        return !ioExecutor.isShutdown && !computationExecutor.isShutdown
    }

    fun getStatus(): String {
        return """
            OneSignalDispatchers Status:
            - IO Executor: ${if (ioExecutor.isShutdown) "Shutdown" else "Active"}
            - Computation Executor: ${if (computationExecutor.isShutdown) "Shutdown" else "Active"}
            - IO Scope: ${if (IOScope.isActive) "Active" else "Cancelled"}
            - Default Scope: ${if (DefaultScope.isActive) "Active" else "Cancelled"}
            """.trimIndent()
    }
}
