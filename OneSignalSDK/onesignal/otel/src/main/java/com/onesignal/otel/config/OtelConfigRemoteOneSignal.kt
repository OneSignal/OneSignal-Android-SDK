package com.onesignal.otel.config

import android.util.Log
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.data.LogRecordData
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
            enableExporterLogging: Boolean
        ): SdkLoggerProvider =
            SdkLoggerProvider
                .builder()
                .setResource(resource)
                .addLogRecordProcessor(
                    OtelConfigShared.LogRecordProcessorConfig.batchLogRecordProcessor(
                        HttpRecordBatchExporter.create(
                            extraHttpHeaders,
                            appId,
                            apiBaseUrl,
                            enableExporterLogging,
                        )
                    )
                ).setLogLimits(OtelConfigShared.LogLimitsConfig::logLimits)
                .build()
    }

    object HttpRecordBatchExporter {
        fun create(
            extraHttpHeaders: Map<String, String>,
            appId: String,
            apiBaseUrl: String,
            enableExporterLogging: Boolean,
        ): LogRecordExporter {
            val exporter =
                LogRecordExporterConfig.otlpHttpLogRecordExporter(
                    extraHttpHeaders,
                    buildEndpoint(apiBaseUrl, appId)
                )

            return if (enableExporterLogging) {
                ExporterLoggingConfig.loggingExporter(exporter)
            } else {
                exporter
            }
        }
    }

    object ExporterLoggingConfig {
        private const val TAG = "OneSignalOtel"

        fun loggingExporter(delegate: LogRecordExporter): LogRecordExporter = LoggingLogRecordExporter(delegate)

        private class LoggingLogRecordExporter(
            private val delegate: LogRecordExporter
        ) : LogRecordExporter {
            override fun export(logs: Collection<LogRecordData>): CompletableResultCode {
                Log.d(TAG, "OTEL export request sent to backend. count=${logs.size}")
                val result = delegate.export(logs)
                result.whenComplete {
                    if (result.isSuccess) {
                        Log.d(TAG, "OTEL export response received: success")
                    } else {
                        val throwable = result.failureThrowable
                        Log.e(
                            TAG,
                            "OTEL export response received: failed${throwable?.let { " - ${it.message}" } ?: ""}",
                            throwable
                        )
                    }
                }
                return result
            }

            override fun flush(): CompletableResultCode = delegate.flush()

            override fun shutdown(): CompletableResultCode = delegate.shutdown()
        }
    }
}
