package com.onesignal.debug.internal.logging.otel

import android.os.Build
import androidx.annotation.RequiresApi
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.debug.internal.logging.otel.attributes.OneSignalOtelFieldsPerEvent
import com.onesignal.debug.internal.logging.otel.attributes.OneSignalOtelFieldsTopLevel
import com.onesignal.debug.internal.logging.otel.config.OtelConfigCrashFile
import com.onesignal.debug.internal.logging.otel.config.OtelConfigRemoteOneSignal
import com.onesignal.debug.internal.logging.otel.config.OtelConfigShared
import com.onesignal.debug.internal.logging.otel.crash.IOneSignalCrashConfigProvider
import io.opentelemetry.api.logs.LogRecordBuilder
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal fun LogRecordBuilder.setAllAttributes(attributes: Map<String, String>): LogRecordBuilder {
    attributes.forEach { this.setAttribute(it.key, it.value) }
    return this
}

internal abstract class OneSignalOpenTelemetryBase(
    private val _osTopLevelFields: OneSignalOtelFieldsTopLevel,
    private val _osPerEventFields: OneSignalOtelFieldsPerEvent,
) : IOneSignalOpenTelemetry {
    private val lock = Any()
    private var sdkCachedValue: OpenTelemetrySdk? = null

    protected suspend fun getSdk(): OpenTelemetrySdk {
        val attributes = _osTopLevelFields.getAttributes()
        synchronized(lock) {
            var localSdk = sdkCachedValue
            if (localSdk != null) {
                return localSdk
            }

            localSdk = getSdkInstance(attributes)
            sdkCachedValue = localSdk
            return localSdk
        }
    }

    protected abstract fun getSdkInstance(attributes: Map<String, String>): OpenTelemetrySdk

    override suspend fun forceFlush(): CompletableResultCode {
        val sdkLoggerProvider = getSdk().sdkLoggerProvider
        return suspendCoroutine {
            it.resume(
                sdkLoggerProvider.forceFlush().join(10, TimeUnit.SECONDS)
            )
        }
    }

    override suspend fun getLogger(): LogRecordBuilder =
        getSdk()
            .sdkLoggerProvider
            .loggerBuilder("loggerBuilder")
            .build()
            .logRecordBuilder()
            .setAllAttributes(_osPerEventFields.getAttributes())
}

@RequiresApi(Build.VERSION_CODES.O)
internal class OneSignalOpenTelemetryRemote(
    private val _configModelStore: ConfigModelStore,
    _osTopLevelFields: OneSignalOtelFieldsTopLevel,
    _osPerEventFields: OneSignalOtelFieldsPerEvent,
) : OneSignalOpenTelemetryBase(_osTopLevelFields, _osPerEventFields),
    IOneSignalOpenTelemetryRemote {
    val extraHttpHeaders by lazy {
        mapOf(
            "OS-App-Id" to _configModelStore.model.appId,
            "x-honeycomb-team" to "", // TODO: REMOVE
        )
    }

    override val logExporter by lazy {
        OtelConfigRemoteOneSignal.HttpRecordBatchExporter.create(extraHttpHeaders)
    }

    override fun getSdkInstance(attributes: Map<String, String>): OpenTelemetrySdk =
        OpenTelemetrySdk
            .builder()
            .setLoggerProvider(
                OtelConfigRemoteOneSignal.SdkLoggerProviderConfig.create(
                    OtelConfigShared.ResourceConfig.create(attributes),
                    extraHttpHeaders
                )
            ).build()
}

internal class OneSignalOpenTelemetryCrashLocal(
    private val _crashPathProvider: IOneSignalCrashConfigProvider,
    _osTopLevelFields: OneSignalOtelFieldsTopLevel,
    _osPerEventFields: OneSignalOtelFieldsPerEvent,
) : OneSignalOpenTelemetryBase(_osTopLevelFields, _osPerEventFields),
    IOneSignalOpenTelemetryCrash {
    override fun getSdkInstance(attributes: Map<String, String>): OpenTelemetrySdk =
        OpenTelemetrySdk
            .builder()
            .setLoggerProvider(
                OtelConfigCrashFile.SdkLoggerProviderConfig.create(
                    OtelConfigShared.ResourceConfig.create(
                        attributes
                    ),
                    _crashPathProvider.path,
                    _crashPathProvider.minFileAgeForReadMillis,
                )
            ).build()
}
