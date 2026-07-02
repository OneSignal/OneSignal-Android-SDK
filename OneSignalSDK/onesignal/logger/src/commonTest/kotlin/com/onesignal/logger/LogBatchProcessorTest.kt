package com.onesignal.logger

import com.onesignal.logger.internal.LogBatchProcessor
import com.onesignal.logger.otlp.EncodableRecord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LogBatchProcessorTest {
    private fun rec(body: String) =
        EncodableRecord(LogSeverity.INFO, body, emptyMap(), 1L)

    @Test
    fun flushExportsBufferedRecordsAsSingleBatch() = runTest {
        val exported = mutableListOf<List<EncodableRecord>>()
        // Very large schedule delay so only the explicit flush triggers an export.
        val proc = LogBatchProcessor(backgroundScope, 100, 100, 100_000L) { exported.add(it) }

        proc.enqueue(rec("a"))
        proc.enqueue(rec("b"))
        proc.flush()

        assertEquals(1, exported.size)
        assertEquals(2, exported[0].size)
    }

    @Test
    fun dropsRecordsWhenQueueIsFull() = runTest {
        val exported = mutableListOf<List<EncodableRecord>>()
        val proc = LogBatchProcessor(backgroundScope, maxQueueSize = 2, maxBatchSize = 100, scheduleDelayMillis = 100_000L) {
            exported.add(it)
        }

        proc.enqueue(rec("a"))
        proc.enqueue(rec("b"))
        proc.enqueue(rec("c")) // dropped: queue already at capacity
        proc.flush()

        assertEquals(2, exported[0].size)
    }
}
