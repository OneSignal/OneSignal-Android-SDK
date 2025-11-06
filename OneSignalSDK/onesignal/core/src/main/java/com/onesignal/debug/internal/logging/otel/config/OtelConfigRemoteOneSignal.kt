package com.onesignal.debug.internal.logging.otel.config

import android.os.Build
import androidx.annotation.RequiresApi
import com.onesignal.debug.internal.logging.otel.config.OtelConfigRemoteOneSignal.SdkLoggerProviderConfig.BASE_URL
import com.onesignal.debug.internal.logging.otel.config.OtelConfigShared.LogLimitsConfig
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.resources.Resource
import java.time.Duration

internal class OtelConfigRemoteOneSignal {
    object LogRecordExporterConfig {
        @RequiresApi(Build.VERSION_CODES.O)
        fun otlpHttpLogRecordExporter(
            headers: Map<String, String>,
            endpoint: String,
        ): LogRecordExporter {
            val builder = OtlpHttpLogRecordExporter.builder()
            headers.forEach { builder.addHeader(it.key, it.value) }
            builder
                .setEndpoint(endpoint)
                .setTimeout(Duration.ofSeconds(10))
            return builder.build()
        }
    }

    object SdkLoggerProviderConfig {
        // TODO: Switch to https://sdklogs.onesignal.com:443/sdk/otel
        const val BASE_URL = "https://api.honeycomb.io:443"

        @RequiresApi(Build.VERSION_CODES.O)
        fun create(
            resource: Resource,
            extraHttpHeaders: Map<String, String>,
        ): SdkLoggerProvider =
            SdkLoggerProvider
                .builder()
                .setResource(resource)
                .addLogRecordProcessor(
                    OtelConfigShared.LogRecordProcessorConfig.batchLogRecordProcessor(
                        HttpRecordBatchExporter.create(extraHttpHeaders)
                    )
                ).setLogLimits(LogLimitsConfig::logLimits)
                .build()
    }

    object HttpRecordBatchExporter {
        @RequiresApi(Build.VERSION_CODES.O)
        fun create(extraHttpHeaders: Map<String, String>) =
            LogRecordExporterConfig.otlpHttpLogRecordExporter(
                extraHttpHeaders,
                "${BASE_URL}/v1/logs"
            )
    }
}
