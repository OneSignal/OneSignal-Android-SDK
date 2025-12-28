package com.onesignal.otel.config

import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import java.time.Duration

internal class OtelConfigRemoteOneSignal {
    object LogRecordExporterConfig {
        private const val EXPORTER_TIMEOUT_SECONDS = 10L

        fun otlpHttpLogRecordExporter(
            headers: Map<String, String>,
            endpoint: String,
        ): LogRecordExporter {
            val builder = OtlpHttpLogRecordExporter.builder()
            headers.forEach { builder.addHeader(it.key, it.value) }
            builder
                .setEndpoint(endpoint)
                .setTimeout(Duration.ofSeconds(EXPORTER_TIMEOUT_SECONDS))
            return builder.build()
        }
    }

    object SdkLoggerProviderConfig {
        // NOTE: Switch to https://sdklogs.onesignal.com:443/sdk/otel when ready
        const val BASE_URL = "https://api.honeycomb.io:443"

        fun create(
            resource: io.opentelemetry.sdk.resources.Resource,
            extraHttpHeaders: Map<String, String>,
        ): SdkLoggerProvider =
            SdkLoggerProvider
                .builder()
                .setResource(resource)
                .addLogRecordProcessor(
                    OtelConfigShared.LogRecordProcessorConfig.batchLogRecordProcessor(
                        HttpRecordBatchExporter.create(extraHttpHeaders)
                    )
                ).setLogLimits(OtelConfigShared.LogLimitsConfig::logLimits)
                .build()
    }

    object HttpRecordBatchExporter {
        fun create(extraHttpHeaders: Map<String, String>) =
            LogRecordExporterConfig.otlpHttpLogRecordExporter(
                extraHttpHeaders,
                "${SdkLoggerProviderConfig.BASE_URL}/v1/logs"
            )
    }
}
