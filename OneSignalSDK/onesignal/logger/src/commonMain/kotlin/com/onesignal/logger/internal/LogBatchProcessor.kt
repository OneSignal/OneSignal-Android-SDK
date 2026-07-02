package com.onesignal.logger.internal

import com.onesignal.logger.otlp.EncodableRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coroutine-based batch processor. Hand-rolled, dependency-light replacement for
 * OpenTelemetry's `BatchLogRecordProcessor`.
 *
 * Records are buffered and exported when either the buffer reaches [maxBatchSize]
 * or [scheduleDelayMillis] elapses. When the buffer exceeds [maxQueueSize], new
 * records are dropped (back-pressure-free, never blocks the caller's pipeline).
 */
internal class LogBatchProcessor(
    private val scope: CoroutineScope,
    private val maxQueueSize: Int,
    private val maxBatchSize: Int,
    private val scheduleDelayMillis: Long,
    private val onExport: suspend (List<EncodableRecord>) -> Unit,
) {
    private val mutex = Mutex()
    private val buffer = ArrayList<EncodableRecord>()

    // CONFLATED: a flush request that arrives while one is pending is coalesced.
    private val flushSignal = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            while (isActive) {
                // Wake on either the schedule delay or an explicit size-triggered signal.
                withTimeoutOrNull(scheduleDelayMillis) { flushSignal.receive() }
                drainAndExport()
            }
        }
    }

    suspend fun enqueue(record: EncodableRecord) {
        val triggerFlush =
            mutex.withLock {
                if (buffer.size >= maxQueueSize) {
                    return // queue full — drop, matching BatchLogRecordProcessor semantics
                }
                buffer.add(record)
                buffer.size >= maxBatchSize
            }
        if (triggerFlush) {
            flushSignal.trySend(Unit)
        }
    }

    /** Exports everything currently buffered. */
    suspend fun flush() = drainAndExport()

    private suspend fun drainAndExport() {
        val batch =
            mutex.withLock {
                if (buffer.isEmpty()) return
                val copy = buffer.toList()
                buffer.clear()
                copy
            }
        onExport(batch)
    }
}
