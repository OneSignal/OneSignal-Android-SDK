package com.onesignal.logger

import com.onesignal.logger.otlp.EncodableRecord
import com.onesignal.logger.otlp.OtlpLogEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OtlpLogEncoderTest {
    @Test
    fun encodesValidOtlpJsonStructure() {
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

        val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        val resourceLogs = root["resourceLogs"]!!.jsonArray
        assertEquals(1, resourceLogs.size)

        val resource = resourceLogs[0].jsonObject["resource"]!!.jsonObject
        val resourceAttrs = resource["attributes"]!!.jsonArray
        // os.name sorts before ossdk.install_id
        assertEquals("os.name", resourceAttrs[0].jsonObject["key"]!!.jsonPrimitive.content)

        val scopeLogs = resourceLogs[0].jsonObject["scopeLogs"]!!.jsonArray
        val scope = scopeLogs[0].jsonObject["scope"]!!.jsonObject
        assertEquals("OneSignalDeviceSDK", scope["name"]!!.jsonPrimitive.content)

        val record = scopeLogs[0].jsonObject["logRecords"]!!.jsonArray[0].jsonObject
        assertEquals("1700000000000000000", record["timeUnixNano"]!!.jsonPrimitive.content)
        assertEquals(17, record["severityNumber"]!!.jsonPrimitive.content.toInt())
        assertEquals("ERROR", record["severityText"]!!.jsonPrimitive.content)
        assertEquals("boom", record["body"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content)
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
    fun contentTypeIsJson() {
        assertTrue(OtlpLogEncoder.CONTENT_TYPE.contains("json"))
    }
}
