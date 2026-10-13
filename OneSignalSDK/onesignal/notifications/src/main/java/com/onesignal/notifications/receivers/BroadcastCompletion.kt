package com.onesignal.notifications.receivers

import android.content.BroadcastReceiver
import android.os.SystemClock
import com.onesignal.common.threading.OneSignalDispatchers
import com.onesignal.common.threading.suspendifyOnIngress
import com.onesignal.debug.internal.logging.Logging
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal fun BroadcastReceiver.runIngressHandoff(
    receiverName: String,
    timeoutMs: Long,
    block: suspend () -> Unit,
) {
    runOrderedIngressHandoff(receiverName, timeoutMs) { _, _ -> block() }
}

internal fun BroadcastReceiver.runOrderedIngressHandoff(
    receiverName: String,
    timeoutMs: Long,
    block: suspend (ordered: Boolean, pendingResult: BroadcastReceiver.PendingResult?) -> Unit,
) {
    OneSignalDispatchers.prewarm()
    // goAsync() nulls the pending result, and isOrderedBroadcast is then false.
    val ordered = isOrderedBroadcast
    val pendingResult = goAsync()
    val completion = BroadcastCompletion(receiverName, pendingResult, timeoutMs)
    // A failed handoff is left open so only the deadline finishes it, never a success path.
    suspendifyOnIngress(
        block = { block(ordered, pendingResult) },
        onSuccess = { completion.finish() },
    )
}

internal class BroadcastCompletion(
    private val receiverName: String,
    private val pendingResult: BroadcastReceiver.PendingResult?,
    timeoutMs: Long? = null,
) {
    private val startedAtMs = SystemClock.elapsedRealtime()
    private val finished = AtomicBoolean(false)
    private val timeoutTask =
        timeoutMs?.let {
            deadlineExecutor.schedule({ finish("deadline") }, it, TimeUnit.MILLISECONDS)
        }

    fun finish(reason: String = "completed") {
        if (!finished.compareAndSet(false, true)) return
        timeoutTask?.cancel(false)
        pendingResult?.finish()
        val durationMs = SystemClock.elapsedRealtime() - startedAtMs
        if (durationMs >= SOFT_DEADLINE_MS) {
            Logging.warn("$receiverName durable handoff finished after ${durationMs}ms ($reason)")
        } else {
            Logging.debug("$receiverName durable handoff finished after ${durationMs}ms ($reason)")
        }
    }

    companion object {
        const val RECONSTRUCTIBLE_WORK_TIMEOUT_MS = 8_000L
        private const val SOFT_DEADLINE_MS = 4_000L
        private val deadlineExecutor =
            ScheduledThreadPoolExecutor(
                1,
            ) { runnable ->
                Thread(runnable, "OS_BroadcastDeadline").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY - 1
                }
            }.apply {
                removeOnCancelPolicy = true
                setKeepAliveTime(30, TimeUnit.SECONDS)
                allowCoreThreadTimeOut(true)
            }
    }
}
