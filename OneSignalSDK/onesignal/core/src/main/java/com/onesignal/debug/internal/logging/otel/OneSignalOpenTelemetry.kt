package com.onesignal.debug.internal.logging.otel

import android.os.Build
import androidx.annotation.RequiresApi
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.debug.internal.logging.otel.attributes.OneSignalOtelTopLevelFields
import com.onesignal.debug.internal.logging.otel.config.OtelConfigCrashFile
import com.onesignal.debug.internal.logging.otel.config.OtelConfigRemoteOneSignal
import com.onesignal.debug.internal.logging.otel.config.OtelConfigShared
import com.onesignal.debug.internal.logging.otel.crash.IOneSignalCrashConfigProvider
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

internal abstract class OneSignalOpenTelemetryBase(
    private val _osFields: OneSignalOtelTopLevelFields
) : IOneSignalOpenTelemetry {
    private val lock = Any()
    private var sdk: OpenTelemetrySdk? = null
    protected suspend fun getSdk(): OpenTelemetrySdk {
        val attributes = _osFields.getAttributes()
        synchronized(lock) {
            var localSdk = sdk
            if (localSdk != null) {
                return localSdk
            }

            localSdk = getSdkInstance(attributes)
            sdk = localSdk
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

    override suspend fun getLogger(): Logger =
        getSdk().sdkLoggerProvider.loggerBuilder("loggerBuilder").build()
}

@RequiresApi(Build.VERSION_CODES.O)
internal class OneSignalOpenTelemetryRemote(
    private val _configModelStore: ConfigModelStore,
    _osFields: OneSignalOtelTopLevelFields,
) : OneSignalOpenTelemetryBase(_osFields),
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
    _osFields: OneSignalOtelTopLevelFields,
) : OneSignalOpenTelemetryBase(_osFields),
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
            )
            .build()
}
