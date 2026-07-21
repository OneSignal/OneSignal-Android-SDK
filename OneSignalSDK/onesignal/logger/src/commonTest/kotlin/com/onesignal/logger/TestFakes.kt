package com.onesignal.logger

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Simple in-memory fakes used across the logger module's common tests.
 */

internal class FakePlatformProvider(
    override var appId: String? = "app-123",
    override var onesignalId: String? = "os-456",
    override var pushSubscriptionId: String? = "push-789",
    override var appState: String = "foreground",
    override var processUptime: Long = 1234L,
    override var currentThreadName: String = "test-thread",
    override var enabledFeatureFlags: List<String> = emptyList(),
    override var crashStoragePath: String = "/tmp/onesignal/crashes",
    override var minFileAgeForReadMillis: Long = 5_000L,
    override var isRemoteLoggingEnabled: Boolean = true,
    override var remoteLogLevel: String? = "ERROR",
    override var isExporterLoggingEnabled: Boolean = false,
    override var apiBaseUrl: String = "https://api.onesignal.com/",
) : ILoggerPlatformProvider {
    var installId: String = "install-abc"

    override suspend fun getInstallId(): String = installId
    override val sdkBase: String = "android"
    override val sdkBaseVersion: String = "5.9.5"
    override val appPackageId: String = "com.example.app"
    override val appVersion: String = "1.0.0"
    override val deviceManufacturer: String = "TestCo"
    override val deviceModel: String = "Pixel-Test"
    override val osName: String = "Android"
    override val osVersion: String = "14"
    override val osBuildId: String = "BUILD123"
    override val sdkWrapper: String? = null
    override val sdkWrapperVersion: String? = null
    override val appIdForHeaders: String get() = appId ?: ""
}

internal class FakeHttpSender(
    var responses: ArrayDeque<LogHttpResponse> = ArrayDeque(),
    var defaultResponse: LogHttpResponse = LogHttpResponse(success = true, statusCode = 200),
) : ILogHttpSender {
    private val mutex = Mutex()
    val sentRequests = mutableListOf<LogHttpRequest>()

    override suspend fun send(request: LogHttpRequest): LogHttpResponse {
        mutex.withLock { sentRequests.add(request) }
        return if (responses.isNotEmpty()) responses.removeFirst() else defaultResponse
    }

    fun lastBodyAsString(): String = sentRequests.last().body.decodeToString()
}

internal class FakeFileStore : ILogFileStore {
    data class Entry(val id: String, val bytes: ByteArray, val ageMillis: Long)

    val entries = mutableListOf<Entry>()
    val deletedIds = mutableListOf<String>()
    private var counter = 0

    /** Age assigned to records saved via [save]; tests can tweak per scenario. */
    var savedAgeMillis: Long = Long.MAX_VALUE

    override fun save(bytes: ByteArray) {
        entries.add(Entry("file-${counter++}", bytes, savedAgeMillis))
    }

    fun seed(id: String, bytes: ByteArray, ageMillis: Long) {
        entries.add(Entry(id, bytes, ageMillis))
    }

    override suspend fun listReadable(minAgeMillis: Long): List<StoredLogFile> =
        entries
            .filter { it.ageMillis >= minAgeMillis && it.id !in deletedIds }
            .map { StoredLogFile(it.id, it.bytes) }

    override suspend fun delete(id: String) {
        deletedIds.add(id)
        entries.removeAll { it.id == id }
    }
}

internal class RecordingLogger : ILogger {
    val messages = mutableListOf<String>()

    override fun error(message: String) { messages.add("E:$message") }
    override fun warn(message: String) { messages.add("W:$message") }
    override fun info(message: String) { messages.add("I:$message") }
    override fun debug(message: String) { messages.add("D:$message") }
}
