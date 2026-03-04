package com.onesignal.otel.config

import io.opentelemetry.contrib.disk.buffering.exporters.LogRecordToDiskExporter
import io.opentelemetry.contrib.disk.buffering.storage.impl.FileLogRecordStorage
import io.opentelemetry.contrib.disk.buffering.storage.impl.FileStorageConfiguration
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import java.io.File
import kotlin.time.Duration.Companion.hours

internal class OtelConfigCrashFile {
    internal object SdkLoggerProviderConfig {
        // NOTE: Only use such as small maxFileAgeForWrite for
        // crashes, as we want to send them as soon as possible
        // without having to wait too long for buffers.
        private const val MAX_FILE_AGE_FOR_WRITE_MILLIS = 2_000L

        fun getFileLogRecordStorage(
            rootDir: String,
            minFileAgeForReadMillis: Long
        ): FileLogRecordStorage =
            FileLogRecordStorage.create(
                File(rootDir),
                FileStorageConfiguration
                    .builder()
                    .setMaxFileAgeForWriteMillis(MAX_FILE_AGE_FOR_WRITE_MILLIS)
                    .setMinFileAgeForReadMillis(minFileAgeForReadMillis)
                    .setMaxFileAgeForReadMillis(72.hours.inWholeMilliseconds)
                    .build()
            )

        fun create(
            resource: io.opentelemetry.sdk.resources.Resource,
            rootDir: String,
            minFileAgeForReadMillis: Long,
        ): SdkLoggerProvider {
            val logToDiskExporter =
                LogRecordToDiskExporter
                    .builder(getFileLogRecordStorage(rootDir, minFileAgeForReadMillis))
                    .build()
            return SdkLoggerProvider
                .builder()
                .setResource(resource)
                .addLogRecordProcessor(
                    BatchLogRecordProcessor.builder(logToDiskExporter).build()
                ).setLogLimits(OtelConfigShared.LogLimitsConfig::logLimits)
                .build()
        }
    }
}
