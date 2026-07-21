package com.onesignal.logger.otlp

import com.onesignal.logger.LogSeverity

/**
 * A record ready to be encoded: severity/body/timestamp plus the FULLY-merged
 * attribute set (per-event + record-specific). Resource/top-level attributes are
 * supplied separately to the encoder.
 */
internal data class EncodableRecord(
    val severity: LogSeverity,
    val body: String,
    val attributes: Map<String, String>,
    val timeUnixNanos: Long,
)

/**
 * Encodes log records into **OTLP/protobuf** bytes (`Content-Type: application/x-protobuf`).
 *
 * This deliberately matches what the OpenTelemetry Java SDK's `OtlpHttpLogRecordExporter`
 * put on the wire (binary protobuf is its default; JSON is opt-in via `exportAsJson()`,
 * which the old `otel` module never called). Producing the same wire format here means
 * the OneSignal ingestion endpoint receives byte-for-byte the same kind of payload it
 * always has — the transport swapped from the OpenTelemetry SDK to this module, but the
 * bytes did not.
 *
 * Only the tiny subset of `opentelemetry-proto`'s `ExportLogsServiceRequest` needed for
 * string-valued log records is implemented, hand-rolled via [ProtoWriter] so it runs on
 * every Kotlin target with no dependency. Field numbers below come from
 * `opentelemetry/proto/logs/v1/logs.proto` and `.../common/v1/common.proto`.
 */
internal object OtlpLogEncoder {
    const val CONTENT_TYPE = "application/x-protobuf"

    private const val SCOPE_NAME = "OneSignalDeviceSDK"

    // Mirrors the old otel module's LogLimits (OtelConfigShared.LogLimitsConfig): the SDK
    // capped each log record at 128 attributes and truncated attribute values to 32,000
    // chars (the exception.stacktrace value can be very long). Applied to log-record
    // attributes only — resource attributes and the body are left untouched, exactly as
    // LogLimits scoped it.
    private const val MAX_ATTRIBUTE_COUNT = 128
    private const val MAX_ATTRIBUTE_VALUE_LENGTH = 32_000

    // ExportLogsServiceRequest
    private const val FIELD_RESOURCE_LOGS = 1

    // ResourceLogs
    private const val FIELD_RL_RESOURCE = 1
    private const val FIELD_RL_SCOPE_LOGS = 2

    // Resource
    private const val FIELD_RESOURCE_ATTRIBUTES = 1

    // ScopeLogs
    private const val FIELD_SL_SCOPE = 1
    private const val FIELD_SL_LOG_RECORDS = 2

    // InstrumentationScope
    private const val FIELD_SCOPE_NAME = 1

    // LogRecord
    private const val FIELD_LR_TIME_UNIX_NANO = 1
    private const val FIELD_LR_SEVERITY_NUMBER = 2
    private const val FIELD_LR_SEVERITY_TEXT = 3
    private const val FIELD_LR_BODY = 5
    private const val FIELD_LR_ATTRIBUTES = 6
    private const val FIELD_LR_OBSERVED_TIME_UNIX_NANO = 11

    // KeyValue
    private const val FIELD_KV_KEY = 1
    private const val FIELD_KV_VALUE = 2

    // AnyValue
    private const val FIELD_ANY_STRING_VALUE = 1

    fun encode(
        resourceAttributes: Map<String, String>,
        records: List<EncodableRecord>,
    ): ByteArray {
        val resourceLogs =
            ProtoWriter().apply {
                writeLengthDelimited(FIELD_RL_RESOURCE, encodeResource(resourceAttributes))
                writeLengthDelimited(FIELD_RL_SCOPE_LOGS, encodeScopeLogs(records))
            }.toByteArray()

        return ProtoWriter().apply {
            writeLengthDelimited(FIELD_RESOURCE_LOGS, resourceLogs)
        }.toByteArray()
    }

    private fun encodeResource(attributes: Map<String, String>): ByteArray {
        val writer = ProtoWriter()
        for ((key, value) in attributes.sortedEntries()) {
            writer.writeLengthDelimited(FIELD_RESOURCE_ATTRIBUTES, encodeKeyValue(key, value))
        }
        return writer.toByteArray()
    }

    private fun encodeScopeLogs(records: List<EncodableRecord>): ByteArray {
        val writer = ProtoWriter()
        writer.writeLengthDelimited(FIELD_SL_SCOPE, encodeScope())
        for (record in records) {
            writer.writeLengthDelimited(FIELD_SL_LOG_RECORDS, encodeLogRecord(record))
        }
        return writer.toByteArray()
    }

    private fun encodeScope(): ByteArray =
        ProtoWriter().apply { writeString(FIELD_SCOPE_NAME, SCOPE_NAME) }.toByteArray()

    private fun encodeLogRecord(record: EncodableRecord): ByteArray {
        val writer = ProtoWriter()
        writer.writeFixed64(FIELD_LR_TIME_UNIX_NANO, record.timeUnixNanos)
        writer.writeVarintField(FIELD_LR_SEVERITY_NUMBER, record.severity.severityNumber.toLong())
        writer.writeString(FIELD_LR_SEVERITY_TEXT, record.severity.severityText)
        writer.writeLengthDelimited(FIELD_LR_BODY, encodeAnyValue(record.body))
        for ((key, value) in record.attributes.sortedEntries().take(MAX_ATTRIBUTE_COUNT)) {
            writer.writeLengthDelimited(FIELD_LR_ATTRIBUTES, encodeKeyValue(key, value.limitValueLength()))
        }
        writer.writeFixed64(FIELD_LR_OBSERVED_TIME_UNIX_NANO, record.timeUnixNanos)
        return writer.toByteArray()
    }

    private fun encodeKeyValue(key: String, value: String): ByteArray =
        ProtoWriter().apply {
            writeString(FIELD_KV_KEY, key)
            writeLengthDelimited(FIELD_KV_VALUE, encodeAnyValue(value))
        }.toByteArray()

    private fun encodeAnyValue(value: String): ByteArray =
        ProtoWriter().apply {
            // proto3 omits empty scalar fields; mirror that so an empty value decodes to
            // an unset oneof exactly as the OpenTelemetry marshaler produces.
            if (value.isNotEmpty()) writeString(FIELD_ANY_STRING_VALUE, value)
        }.toByteArray()

    // Sort for deterministic output (stable payloads, easier testing/diffing).
    private fun Map<String, String>.sortedEntries(): List<Map.Entry<String, String>> = entries.sortedBy { it.key }

    private fun String.limitValueLength(): String =
        if (length > MAX_ATTRIBUTE_VALUE_LENGTH) substring(0, MAX_ATTRIBUTE_VALUE_LENGTH) else this
}
