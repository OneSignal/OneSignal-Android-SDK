package com.onesignal.logger

import com.onesignal.logger.otlp.EncodableRecord
import com.onesignal.logger.otlp.OtlpLogEncoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OtlpLogEncoderTest {
    @Test
    fun encodesValidOtlpProtobufStructure() {
        val bytes =
            OtlpLogEncoder.encode(
                resourceAttributes = mapOf("ossdk.install_id" to "abc", "os.name" to "Android"),
                records =
                listOf(
                    EncodableRecord(
                        severity = LogSeverity.ERROR,
                        body = "boom",
                        attributes = mapOf("log.level" to "ERROR", "log.message" to "boom"),
                        timeUnixNanos = 1700000000000000000L,
                    ),
                ),
            )

        // ExportLogsServiceRequest.resource_logs (1) -> ResourceLogs
        val resourceLogs = parseProto(bytes).message(1)

        // ResourceLogs.resource (1) -> Resource, attributes (1) repeated KeyValue, sorted by key.
        val resourceAttrs = resourceLogs.message(1).all(1).map { parseProto(it.bytes()) }
        // os.name sorts before ossdk.install_id
        assertEquals("os.name", resourceAttrs[0].string(1))
        assertEquals("Android", resourceAttrs[0].message(2).string(1))
        assertEquals("ossdk.install_id", resourceAttrs[1].string(1))

        // ResourceLogs.scope_logs (2) -> ScopeLogs
        val scopeLogs = resourceLogs.message(2)
        assertEquals("OneSignalDeviceSDK", scopeLogs.message(1).string(1))

        // ScopeLogs.log_records (2) -> LogRecord
        val record = scopeLogs.message(2)
        // time_unix_nano (1, fixed64) and observed_time_unix_nano (11, fixed64)
        assertEquals(1700000000000000000L, record.first(1).varint)
        assertEquals(1700000000000000000L, record.first(11).varint)
        // severity_number (2, varint) and severity_text (3, string)
        assertEquals(17L, record.first(2).varint)
        assertEquals("ERROR", record.string(3))
        // body (5) -> AnyValue.string_value (1)
        assertEquals("boom", record.message(5).string(1))
        // attributes (6) repeated, sorted: log.level then log.message
        val recordAttrs = record.all(6).map { parseProto(it.bytes()) }
        assertEquals("log.level", recordAttrs[0].string(1))
        assertEquals("log.message", recordAttrs[1].string(1))
        assertEquals("boom", recordAttrs[1].message(2).string(1))
    }

    @Test
    fun emptyStringValueOmittedToMatchProto3() {
        // An empty attribute value must produce an AnyValue with NO string_value field
        // set (matching proto3 / the OpenTelemetry marshaler), i.e. an empty message.
        val bytes =
            OtlpLogEncoder.encode(
                resourceAttributes = emptyMap(),
                records =
                listOf(
                    EncodableRecord(
                        severity = LogSeverity.INFO,
                        body = "b",
                        attributes = mapOf("empty" to ""),
                        timeUnixNanos = 1L,
                    ),
                ),
            )
        val record = parseProto(bytes).message(1).message(2).message(2)
        val attr = parseProto(record.all(6).single().bytes())
        assertEquals("empty", attr.string(1))
        // value (2) is present but its AnyValue message is empty (no field 1).
        assertTrue(attr.message(2).all(1).isEmpty())
    }

    @Test
    fun severityNumbersMatchOtelScheme() {
        assertEquals(1, LogSeverity.TRACE.severityNumber)
        assertEquals(5, LogSeverity.DEBUG.severityNumber)
        assertEquals(9, LogSeverity.INFO.severityNumber)
        assertEquals(13, LogSeverity.WARN.severityNumber)
        assertEquals(17, LogSeverity.ERROR.severityNumber)
        assertEquals(21, LogSeverity.FATAL.severityNumber)
    }

    @Test
    fun verboseMapsToTrace() {
        assertEquals(LogSeverity.TRACE, LogSeverity.fromLevelName("VERBOSE"))
        assertEquals(LogSeverity.INFO, LogSeverity.fromLevelName("nonsense"))
    }

    @Test
    fun contentTypeIsProtobuf() {
        assertEquals("application/x-protobuf", OtlpLogEncoder.CONTENT_TYPE)
    }

    @Test
    fun truncatesAttributeValuesToLogLimit() {
        // Matches the old otel LogLimits.maxAttributeValueLength (32,000). A long
        // exception.stacktrace value must be truncated on the wire.
        val longValue = "x".repeat(40_000)
        val bytes =
            OtlpLogEncoder.encode(
                resourceAttributes = emptyMap(),
                records =
                listOf(
                    EncodableRecord(
                        severity = LogSeverity.ERROR,
                        body = "boom",
                        attributes = mapOf("exception.stacktrace" to longValue),
                        timeUnixNanos = 1L,
                    ),
                ),
            )
        val record = parseProto(bytes).message(1).message(2).message(2)
        val attr = parseProto(record.all(6).single().bytes())
        assertEquals("exception.stacktrace", attr.string(1))
        assertEquals(32_000, attr.message(2).string(1).length)
    }

    @Test
    fun capsAttributeCountToLogLimit() {
        // Matches the old otel LogLimits.maxNumberOfAttributes (128).
        val manyAttrs = (0 until 200).associate { "k${it.toString().padStart(3, '0')}" to "v$it" }
        val bytes =
            OtlpLogEncoder.encode(
                resourceAttributes = emptyMap(),
                records =
                listOf(
                    EncodableRecord(
                        severity = LogSeverity.INFO,
                        body = "b",
                        attributes = manyAttrs,
                        timeUnixNanos = 1L,
                    ),
                ),
            )
        val record = parseProto(bytes).message(1).message(2).message(2)
        assertEquals(128, record.all(6).size)
    }
}
