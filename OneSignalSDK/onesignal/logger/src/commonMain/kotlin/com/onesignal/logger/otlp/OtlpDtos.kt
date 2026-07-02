package com.onesignal.logger.otlp

import kotlinx.serialization.Serializable

/**
 * Minimal OTLP/JSON data model for the logs export request.
 *
 * This is a hand-rolled subset of `opentelemetry-proto`'s `ExportLogsServiceRequest`,
 * sufficient for shipping string-valued log records. Keeping it tiny and explicit
 * (rather than depending on the OpenTelemetry SDK) is what lets the same encoder run
 * on every Kotlin target.
 *
 * Field names match the OTLP/JSON spec exactly (camelCase). uint64 fields are encoded
 * as strings per the OTLP/JSON convention.
 *
 * See: https://opentelemetry.io/docs/specs/otlp/#otlphttp and
 *      https://github.com/open-telemetry/opentelemetry-proto
 */
@Serializable
internal data class OtlpExportLogsRequest(
    val resourceLogs: List<OtlpResourceLogs>,
)

@Serializable
internal data class OtlpResourceLogs(
    val resource: OtlpResource,
    val scopeLogs: List<OtlpScopeLogs>,
)

@Serializable
internal data class OtlpResource(
    val attributes: List<OtlpKeyValue>,
)

@Serializable
internal data class OtlpScopeLogs(
    val scope: OtlpScope,
    val logRecords: List<OtlpLogRecord>,
)

@Serializable
internal data class OtlpScope(
    val name: String,
)

@Serializable
internal data class OtlpLogRecord(
    val timeUnixNano: String,
    val observedTimeUnixNano: String,
    val severityNumber: Int,
    val severityText: String,
    val body: OtlpAnyValue,
    val attributes: List<OtlpKeyValue>,
)

@Serializable
internal data class OtlpKeyValue(
    val key: String,
    val value: OtlpAnyValue,
)

@Serializable
internal data class OtlpAnyValue(
    val stringValue: String,
)
