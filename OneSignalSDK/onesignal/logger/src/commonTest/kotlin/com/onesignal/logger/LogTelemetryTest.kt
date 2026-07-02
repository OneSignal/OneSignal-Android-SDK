package com.onesignal.logger

import com.onesignal.logger.attributes.LogFieldsPerEvent
import com.onesignal.logger.attributes.LogFieldsTopLevel
import com.onesignal.logger.internal.LogTelemetryCrashImpl
import com.onesignal.logger.internal.LogTelemetryRemoteImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogTelemetryTest {
    private fun remote(
        scope: kotlinx.coroutines.CoroutineScope,
        provider: FakePlatformProvider,
        http: FakeHttpSender,
    ) = LogTelemetryRemoteImpl(
        platformProvider = provider,
        httpSender = http,
        topLevelFields = LogFieldsTopLevel(provider),
        perEventFields = LogFieldsPerEvent(provider),
        scope = scope,
    )

    @Test
    fun emitThenForceFlushPostsOtlpJsonToCorrectEndpoint() = runTest {
        val provider = FakePlatformProvider()
        val http = FakeHttpSender()
        val telemetry = remote(backgroundScope, provider, http)

        telemetry.emit(LogRecord(LogSeverity.ERROR, "hello", mapOf("custom" to "v")))
        telemetry.forceFlush()

        assertEquals(1, http.sentRequests.size)
        val req = http.sentRequests[0]
        assertEquals("https://api.onesignal.com/sdk/log?app_id=app-123", req.url)
        assertEquals("application/json", req.contentType)
        assertEquals("onesignal/android/5.9.5", req.headers["SDK-Version"])

        val body = http.lastBodyAsString()
        assertTrue(body.contains("hello"))
        assertTrue(body.contains("custom"))
        // resource attribute attached
        assertTrue(body.contains("ossdk.install_id"))
    }

    @Test
    fun exportEncodedPostsRawBytes() = runTest {
        val provider = FakePlatformProvider()
        val http = FakeHttpSender()
        val telemetry = remote(backgroundScope, provider, http)

        val ok = telemetry.exportEncoded("raw-bytes".encodeToByteArray())

        assertTrue(ok)
        assertEquals("raw-bytes", http.lastBodyAsString())
    }

    @Test
    fun exportEncodedReturnsFalseOnHttpFailure() = runTest {
        val provider = FakePlatformProvider()
        val http = FakeHttpSender(defaultResponse = LogHttpResponse(success = false, statusCode = 500))
        val telemetry = remote(backgroundScope, provider, http)

        assertFalse(telemetry.exportEncoded("x".encodeToByteArray()))
    }

    @Test
    fun crashTelemetryWritesEncodedRecordToFileStore() = runTest {
        val provider = FakePlatformProvider()
        val store = FakeFileStore()
        val telemetry =
            LogTelemetryCrashImpl(store, LogFieldsTopLevel(provider), LogFieldsPerEvent(provider))

        telemetry.emit(LogRecord(LogSeverity.FATAL, "crash!", mapOf("exception.type" to "X")))

        assertEquals(1, store.entries.size)
        val json = store.entries[0].bytes.decodeToString()
        assertTrue(json.contains("crash!"))
        assertTrue(json.contains("exception.type"))
        assertTrue(json.contains("ossdk.install_id"))
    }
}
