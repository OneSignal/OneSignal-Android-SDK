package com.onesignal.logger.otlp

import com.onesignal.logger.LogSeverity
import kotlinx.serialization.json.Json

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
 * Encodes log records into OTLP/JSON bytes.
 *
 * This is the single seam that decides the wire format. The OneSignal ingestion
 * endpoint historically received OTLP protobuf from the OpenTelemetry SDK; OTLP/JSON
 * is part of the same spec and is dramatically simpler to produce on every Kotlin
 * target. If the backend turns out to require protobuf, ONLY this class needs to
 * change (swap [CONTENT_TYPE] and the body bytes) — nothing else in the pipeline.
 */
internal object OtlpLogEncoder {
    const val CONTENT_TYPE = "application/json"

    private const val SCOPE_NAME = "OneSignalDeviceSDK"

    private val json = Json {
        encodeDefaults = true
    }

    fun encode(
        resourceAttributes: Map<String, String>,
        records: List<EncodableRecord>,
    ): ByteArray {
        val request = OtlpExportLogsRequest(
            resourceLogs = listOf(
                OtlpResourceLogs(
                    resource = OtlpResource(resourceAttributes.toKeyValues()),
                    scopeLogs = listOf(
                        OtlpScopeLogs(
                            scope = OtlpScope(SCOPE_NAME),
                            logRecords = records.map { it.toOtlp() },
                        ),
                    ),
                ),
            ),
        )
        return json.encodeToString(OtlpExportLogsRequest.serializer(), request).encodeToByteArray()
    }

    private fun EncodableRecord.toOtlp(): OtlpLogRecord {
        val time = timeUnixNanos.toString()
        return OtlpLogRecord(
            timeUnixNano = time,
            observedTimeUnixNano = time,
            severityNumber = severity.severityNumber,
            severityText = severity.severityText,
            body = OtlpAnyValue(body),
            attributes = attributes.toKeyValues(),
        )
    }

    private fun Map<String, String>.toKeyValues(): List<OtlpKeyValue> =
        // Sort for deterministic output (stable payloads, easier testing/diffing).
        entries.sortedBy { it.key }.map { OtlpKeyValue(it.key, OtlpAnyValue(it.value)) }
}
