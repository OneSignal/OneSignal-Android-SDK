package com.onesignal.notifications.receivers

import android.content.BroadcastReceiver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.onesignal.debug.internal.logging.Logging
import java.util.concurrent.atomic.AtomicBoolean

internal class BroadcastCompletion(
    private val receiverName: String,
    private val pendingResult: BroadcastReceiver.PendingResult?,
    timeoutMs: Long? = null,
) {
    private val startedAtMs = SystemClock.elapsedRealtime()
    private val finished = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { finish("deadline") }

    init {
        if (timeoutMs != null) handler.postDelayed(timeout, timeoutMs)
    }

    fun finish(reason: String = "completed") {
        if (!finished.compareAndSet(false, true)) return
        handler.removeCallbacks(timeout)
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
    }
}
