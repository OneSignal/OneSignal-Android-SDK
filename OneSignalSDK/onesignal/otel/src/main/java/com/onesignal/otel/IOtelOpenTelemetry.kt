package com.onesignal.otel

/**
 * Platform-agnostic OpenTelemetry handle.
 *
 * OpenTelemetry Java types are intentionally absent from this public surface so the
 * `:otel` module can relocate `io.opentelemetry` into a private package (SDK-5006).
 * Host apps and `:core` must not compile against those types.
 */
interface IOtelOpenTelemetry {
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
interface IOtelOpenTelemetryRemote : IOtelOpenTelemetry
