package com.onesignal.otel.config

import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import java.time.Duration

internal class OtelConfigRemoteOneSignal {
    companion object {
        const val OTEL_PATH = "sdk/otel"

        fun buildEndpoint(apiBaseUrl: String, appId: String): String =
            "$apiBaseUrl$OTEL_PATH/v1/logs?app_id=$appId"
    }

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
        fun create(
            resource: io.opentelemetry.sdk.resources.Resource,
            extraHttpHeaders: Map<String, String>,
            appId: String,
            apiBaseUrl: String,
        ): SdkLoggerProvider =
            SdkLoggerProvider
                .builder()
                .setResource(resource)
                .addLogRecordProcessor(
                    OtelConfigShared.LogRecordProcessorConfig.batchLogRecordProcessor(
                        HttpRecordBatchExporter.create(extraHttpHeaders, appId, apiBaseUrl)
                    )
                ).setLogLimits(OtelConfigShared.LogLimitsConfig::logLimits)
                .build()
    }

    object HttpRecordBatchExporter {
        fun create(extraHttpHeaders: Map<String, String>, appId: String, apiBaseUrl: String) =
            LogRecordExporterConfig.otlpHttpLogRecordExporter(
                extraHttpHeaders,
                buildEndpoint(apiBaseUrl, appId)
            )
    }
}
