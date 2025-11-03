package com.onesignal.debug.internal.logging.otel

import android.os.Build
import androidx.annotation.RequiresApi
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.LogLimits
import io.opentelemetry.sdk.logs.LogRecordProcessor
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.semconv.ServiceAttributes
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal object LogLimitsConfig {
    fun logLimits(): LogLimits =
        LogLimits
            .builder()
            .setMaxNumberOfAttributes(128)
            // We want a high value max length as the exception.stacktrace
            // value can be lengthly.
            .setMaxAttributeValueLength(32000)
            .build()
}

internal object LogRecordProcessorConfig {
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

internal object LogRecordExporterConfig {
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

internal object SdkLoggerProviderConfig {
    // TODO: Switch to sdklogs.onesignal.com
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
                LogRecordProcessorConfig.batchLogRecordProcessor(
                    LogRecordExporterConfig.otlpHttpLogRecordExporter(
                        extraHttpHeaders,
                        "${BASE_URL}/v1/logs"
                    )
                )
            ).setLogLimits(LogLimitsConfig::logLimits)
            .build()
}

internal object ResourceConfig {
    fun create(configModel: ConfigModel): Resource =
        Resource
            .getDefault()
            .toBuilder()
//            .put(ServiceAttributes.SERVICE_NAME, "OneSignalDeviceSDK")
            .put(ServiceAttributes.SERVICE_NAME, "OS-Android-SDK-Test")
            .put("ossdk.app_id", configModel.appId)
            // TODO: other fields
            // TODO: Why not set all top level fields here? Use a top level provider
            .build()
}

@RequiresApi(Build.VERSION_CODES.O)
internal class OneSignalOpenTelemetry(
    private val _configModelStore: ConfigModelStore,
) : IOneSignalOpenTelemetry {
    private val sdk: OpenTelemetrySdk by lazy {
        val extraHttpHeaders =
            mapOf(
                "OS-App-Id" to "value",
                "x-honeycomb-team" to "", // TODO: REMOVE
            )
        OpenTelemetrySdk
            .builder()
            .setLoggerProvider(
                SdkLoggerProviderConfig.create(
                    ResourceConfig.create(_configModelStore.model),
                    extraHttpHeaders
                )
            ).build()
    }

    override val logger: Logger
        get() = sdk.sdkLoggerProvider.loggerBuilder("loggerBuilder").build()

    override suspend fun forceFlush(): CompletableResultCode =
        suspendCoroutine {
            it.resume(
                sdk.sdkLoggerProvider.forceFlush().join(10, TimeUnit.SECONDS)
            )
        }
}
