package com.onesignal.otel

import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.export.LogRecordExporter

/**
 * OpenTelemetry SDK surface used only inside `:otel`.
 *
 * Kept `internal` so `:core` and host apps never compile against `io.opentelemetry`
 * types. That boundary is what lets the release AAR relocate those packages without
 * leaking them onto the consumer compile/runtime classpath (SDK-5006).
 */
internal interface IOtelSdkTelemetry : IOtelOpenTelemetry {
    /**
     * Gets a LogRecordBuilder for creating log records.
     * This is a suspend function as it may need to initialize the SDK on first call.
     */
    suspend fun getLogger(): LogRecordBuilder

    /**
     * Forces a flush of all pending log records.
     * This ensures all buffered logs are exported immediately.
     */
    suspend fun forceFlush(): CompletableResultCode
}

internal interface IOtelSdkCrashTelemetry : IOtelOpenTelemetryCrash, IOtelSdkTelemetry

internal interface IOtelSdkRemoteTelemetry : IOtelOpenTelemetryRemote, IOtelSdkTelemetry {
    val logExporter: LogRecordExporter
}
