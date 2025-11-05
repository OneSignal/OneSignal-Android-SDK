package com.onesignal.debug.internal.logging.otel.config

import android.os.Build
import androidx.annotation.RequiresApi
import com.onesignal.debug.internal.logging.otel.config.OtelConfigShared.LogLimitsConfig
import com.onesignal.debug.internal.logging.otel.config.OtelConfigShared.LogLimitsConfig.logLimits
import io.opentelemetry.contrib.disk.buffering.exporters.LogRecordToDiskExporter
import io.opentelemetry.contrib.disk.buffering.storage.impl.FileLogRecordStorage
import io.opentelemetry.contrib.disk.buffering.storage.impl.FileStorageConfiguration
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import java.io.File
import kotlin.time.Duration.Companion.hours

class OtelConfigCrashFile {
    internal object SdkLoggerProviderConfig {
        fun getFileLogRecordStorage(
            rootDir: String,
            minFileAgeForReadMillis: Long
        ): FileLogRecordStorage =
            FileLogRecordStorage.create(
                File(rootDir),
                FileStorageConfiguration
                    .builder()
                    // NOTE: Only use such as small maxFileAgeForWrite for
                    // crashes, as we want to send them as soon as possible
                    // without have to wait too long for buffers.
                    .setMaxFileAgeForWriteMillis(2_000)
                    .setMinFileAgeForReadMillis(minFileAgeForReadMillis)
                    .setMaxFileAgeForReadMillis(72.hours.inWholeMilliseconds)
                    .build()
            )

        fun create(
            resource: Resource,
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
                ).setLogLimits(LogLimitsConfig::logLimits)
                .build()
        }
    }
}
