package com.onesignal.common.threading

import kotlinx.coroutines.isActive

private const val TEST_READY_TIMEOUT_MS = 2_000L
private const val TEST_READY_POLL_MS = 5L
private const val NOT_INITIALIZED = "Not initialized"
private const val UNKNOWN_ERROR = "Unknown error"
private const val NOT_AVAILABLE = "n/a"
private const val STACK_KB_PER_THREAD = 1024
private val READY_STATUSES = setOf("Active", "Fallback")

internal fun performanceMetrics(current: DispatcherPools): String {
    val io = current.io.executor()
    val default = current.defaultLane.executor()
    val serial = current.serialIO.executor()
    val ingress = current.ingress.executor()
    val built = listOfNotNull(io, default, serial, ingress)
    return """
        OneSignalDispatchers Performance Metrics:
        - IO Pool: ${io?.let { "${it.activeCount}/${it.corePoolSize}" } ?: NOT_AVAILABLE} active/core threads
        - IO Queue: ${io?.queue?.size ?: NOT_AVAILABLE} pending tasks
        - Default Pool: ${default?.let { "${it.activeCount}/${it.corePoolSize}" } ?: NOT_AVAILABLE} active/core threads
        - Default Queue: ${default?.queue?.size ?: NOT_AVAILABLE} pending tasks
        - SerialIO Queue: ${serial?.queue?.size ?: NOT_AVAILABLE} pending tasks
        - Ingress Queue: ${ingress?.queue?.size ?: NOT_AVAILABLE} pending tasks
        - Total completed tasks: ${built.sumOf { it.completedTaskCount }}
        - Memory usage: ~${built.sumOf { it.activeCount } * STACK_KB_PER_THREAD}KB (thread stacks, ~1MB each)
    """.trimIndent()
}

internal fun dispatcherStatus(current: DispatcherPools): String {
    return """
        OneSignalDispatchers Status:
        - IO Executor: ${current.io.status()}
        - Default Executor: ${current.defaultLane.status()}
        - SerialIO Executor: ${current.serialIO.status()}
        - Ingress Executor: ${current.ingress.status()}
        - IO Scope: ${scopeStatus("IOScope") { current.ioScope.isActive }}
        - Default Scope: ${scopeStatus("DefaultScope") { current.defaultScope.isActive }}
        - SerialIO Scope: ${scopeStatus("SerialIOScope") { current.serialIOScope.isActive }}
        - Ingress Scope: ${scopeStatus("IngressScope") { current.ingressScope.isActive }}
    """.trimIndent()
}

internal fun awaitPoolsReady(
    pools: () -> DispatcherPools,
    timeoutMs: Long = TEST_READY_TIMEOUT_MS,
): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        val current = pools()
        if (listOf(current.io, current.defaultLane, current.serialIO, current.ingress).all { it.status() in READY_STATUSES }) {
            return true
        }
        Thread.sleep(TEST_READY_POLL_MS)
    }
    return false
}

@Suppress("TooGenericExceptionCaught")
internal fun executorStatus(
    name: String,
    isShutdown: () -> Boolean,
): String =
    try {
        if (isShutdown()) "Shutdown" else "Active"
    } catch (e: Exception) {
        "$name $NOT_INITIALIZED ${e.message ?: UNKNOWN_ERROR}"
    }

@Suppress("TooGenericExceptionCaught")
internal fun scopeStatus(
    name: String,
    isActive: () -> Boolean,
): String =
    try {
        if (isActive()) "Active" else "Cancelled"
    } catch (e: Exception) {
        "$name $NOT_INITIALIZED ${e.message ?: UNKNOWN_ERROR}"
    }
