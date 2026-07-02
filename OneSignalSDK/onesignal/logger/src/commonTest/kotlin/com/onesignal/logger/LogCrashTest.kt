package com.onesignal.logger

import com.onesignal.logger.attributes.LogFieldsPerEvent
import com.onesignal.logger.attributes.LogFieldsTopLevel
import com.onesignal.logger.internal.LogTelemetryRemoteImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogCrashTest {
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
    fun reporterSavesFatalRecordWithExceptionAttributes() = runTest {
        val provider = FakePlatformProvider()
        val store = FakeFileStore()
        val crashTelemetry = LoggerFactory.createCrashLocalTelemetry(provider, store)
        val reporter = LoggerFactory.createCrashReporter(crashTelemetry, RecordingLogger())

        reporter.saveCrash(
            CrashData(
                threadName = "main",
                exceptionType = "java.lang.NullPointerException",
                exceptionMessage = "npe message",
                stacktrace = "stack...",
            ),
        )

        assertEquals(1, store.entries.size)
        val json = store.entries[0].bytes.decodeToString()
        assertTrue(json.contains("npe message"))
        assertTrue(json.contains("java.lang.NullPointerException"))
        assertTrue(json.contains("ossdk.exception.thread.name"))
    }

    @Test
    fun uploaderSendsReadableReportsAndDeletesOnSuccess() = runTest {
        val provider = FakePlatformProvider(minFileAgeForReadMillis = 0)
        val store = FakeFileStore()
        store.seed("f1", "payload1".encodeToByteArray(), ageMillis = Long.MAX_VALUE)
        store.seed("f2", "payload2".encodeToByteArray(), ageMillis = Long.MAX_VALUE)
        val http = FakeHttpSender()
        val uploader =
            LoggerFactory.createCrashUploader(provider, remote(backgroundScope, provider, http), store, RecordingLogger())

        uploader.start()

        assertEquals(2, http.sentRequests.size)
        assertTrue("f1" in store.deletedIds)
        assertTrue("f2" in store.deletedIds)
    }

    @Test
    fun uploaderStopsOnFailureAndKeepsReports() = runTest {
        val provider = FakePlatformProvider()
        val store = FakeFileStore()
        store.seed("f1", "p1".encodeToByteArray(), ageMillis = Long.MAX_VALUE)
        store.seed("f2", "p2".encodeToByteArray(), ageMillis = Long.MAX_VALUE)
        val http = FakeHttpSender(defaultResponse = LogHttpResponse(success = false, statusCode = 500))
        val uploader =
            LoggerFactory.createCrashUploader(provider, remote(backgroundScope, provider, http), store, RecordingLogger())

        uploader.start()

        // Two passes (start sends, then again after the read-age delay), each stops at f1.
        assertEquals(2, http.sentRequests.size)
        assertTrue(store.deletedIds.isEmpty())
    }

    @Test
    fun uploaderNoOpWhenRemoteLoggingDisabled() = runTest {
        val provider = FakePlatformProvider(remoteLogLevel = "NONE")
        val store = FakeFileStore()
        store.seed("f1", "p".encodeToByteArray(), ageMillis = Long.MAX_VALUE)
        val http = FakeHttpSender()
        val uploader =
            LoggerFactory.createCrashUploader(provider, remote(backgroundScope, provider, http), store, RecordingLogger())

        uploader.start()

        assertEquals(0, http.sentRequests.size)
    }
}
