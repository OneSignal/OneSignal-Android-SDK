package com.onesignal.otel

import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.export.LogRecordExporter

/**
 * Platform-agnostic OpenTelemetry interface.
 */
interface IOtelOpenTelemetry {
    /**
     * Gets a LogRecordBuilder for creating log records.
     * This is a suspend function as it may need to initialize the SDK on first call.
     *
     * @return A LogRecordBuilder instance for building log records
     */
    suspend fun getLogger(): LogRecordBuilder

    /**
     * Forces a flush of all pending log records.
     * This ensures all buffered logs are exported immediately.
     *
     * @return A CompletableResultCode indicating the flush operation result
     */
    suspend fun forceFlush(): CompletableResultCode

    /**
     * Shuts down the underlying OpenTelemetry SDK, flushing pending data
     * and releasing resources (exporters, logger providers, etc.).
     * After this call the instance must not be reused.
     */
    fun shutdown()
}

/**
 * Interface for crash-specific OpenTelemetry (local file storage).
 */
interface IOtelOpenTelemetryCrash : IOtelOpenTelemetry

/**
 * Interface for remote OpenTelemetry (network export).
 */
interface IOtelOpenTelemetryRemote : IOtelOpenTelemetry {
    val logExporter: LogRecordExporter
}
