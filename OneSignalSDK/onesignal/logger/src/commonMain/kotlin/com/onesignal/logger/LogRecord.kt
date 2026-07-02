package com.onesignal.logger

/**
 * A single, fully platform-agnostic log record.
 *
 * This replaces the OpenTelemetry `LogRecordBuilder` / `LogRecordData` types that
 * the old `otel` module leaked across its public surface. A record carries only
 * its own (record-specific) attributes; the telemetry implementation merges in
 * the per-event and top-level/resource attributes at emit/encode time.
 *
 * @property severity log severity
 * @property body human-readable message (becomes the OTLP log body)
 * @property attributes record-specific attributes (e.g. exception.* fields)
 * @property timestampNanos event time in nanoseconds since the Unix epoch; when
 *   `null` the telemetry stamps it at emit time.
 */
data class LogRecord(
    val severity: LogSeverity,
    val body: String,
    val attributes: Map<String, String> = emptyMap(),
    val timestampNanos: Long? = null,
)
