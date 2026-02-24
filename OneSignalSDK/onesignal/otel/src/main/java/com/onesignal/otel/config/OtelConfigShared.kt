package com.onesignal.otel.config

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
                .put(ServiceAttributes.SERVICE_NAME, "OneSignalDeviceSDK")
                .putAll(attributes)
                .build()
    }

    object LogRecordProcessorConfig {
        private const val MAX_QUEUE_SIZE = 100
        private const val MAX_EXPORT_BATCH_SIZE = 100
        private const val EXPORTER_TIMEOUT_SECONDS = 30L
        private const val SCHEDULE_DELAY_SECONDS = 1L

        fun batchLogRecordProcessor(logRecordExporter: LogRecordExporter): LogRecordProcessor =
            BatchLogRecordProcessor
                .builder(logRecordExporter)
                .setMaxQueueSize(MAX_QUEUE_SIZE)
                .setMaxExportBatchSize(MAX_EXPORT_BATCH_SIZE)
                .setExporterTimeout(Duration.ofSeconds(EXPORTER_TIMEOUT_SECONDS))
                .setScheduleDelay(Duration.ofSeconds(SCHEDULE_DELAY_SECONDS))
                .build()
    }

    object LogLimitsConfig {
        private const val MAX_NUMBER_OF_ATTRIBUTES = 128

        // We want a high value max length as the exception.stacktrace
        // value can be lengthly.
        private const val MAX_ATTRIBUTE_VALUE_LENGTH = 32000

        fun logLimits(): LogLimits =
            LogLimits
                .builder()
                .setMaxNumberOfAttributes(MAX_NUMBER_OF_ATTRIBUTES)
                .setMaxAttributeValueLength(MAX_ATTRIBUTE_VALUE_LENGTH)
                .build()
    }
}
