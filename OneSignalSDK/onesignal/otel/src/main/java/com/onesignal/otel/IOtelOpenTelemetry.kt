package com.onesignal.otel

import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.export.LogRecordExporter

/**
 * Platform-agnostic OpenTelemetry interface.
 */
interface IOtelOpenTelemetry {
    suspend fun getLogger(): LogRecordBuilder
    suspend fun forceFlush(): CompletableResultCode
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
