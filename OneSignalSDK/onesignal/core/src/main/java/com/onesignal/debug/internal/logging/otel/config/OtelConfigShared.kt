package com.onesignal.debug.internal.logging.otel.config

import android.os.Build
import androidx.annotation.RequiresApi
import io.opentelemetry.sdk.logs.LogLimits
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.resources.ResourceBuilder
import io.opentelemetry.semconv.ServiceAttributes
import java.time.Duration

internal fun ResourceBuilder.putAll(attributes: Map<String, String>): ResourceBuilder {
    attributes.forEach { this.put(it.key, it.value) }
    return this
}

internal class OtelConfigShared {
    object ResourceConfig {
        fun create(attributes: Map<String, String>): Resource =
            Resource
                .getDefault()
                .toBuilder()
                //            .put(ServiceAttributes.SERVICE_NAME, "OneSignalDeviceSDK")
                .put(ServiceAttributes.SERVICE_NAME, "OS-Android-SDK-Test")
                .putAll(attributes)
                .build()
    }

    object LogRecordProcessorConfig {
        @RequiresApi(Build.VERSION_CODES.O)
        fun batchLogRecordProcessor(logRecordExporter: LogRecordExporter): LogRecordProcessor =
            BatchLogRecordProcessor
                .builder(logRecordExporter)
                .setMaxQueueSize(100)
                .setMaxExportBatchSize(100)
                .setExporterTimeout(Duration.ofSeconds(30))
                .setScheduleDelay(Duration.ofSeconds(1))
                .build()
    }

    object LogLimitsConfig {
        fun logLimits(): LogLimits =
            LogLimits
                .builder()
                .setMaxNumberOfAttributes(128)
                // We want a high value max length as the exception.stacktrace
                // value can be lengthly.
                .setMaxAttributeValueLength(32000)
                .build()
    }
}
