package com.onesignal.debug.internal.logging.otel

import android.os.Build
import androidx.annotation.RequiresApi
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.debug.internal.logging.otel.config.OtelConfigCrashFile
import com.onesignal.debug.internal.logging.otel.config.OtelConfigRemoteOneSignal
import com.onesignal.debug.internal.logging.otel.config.OtelConfigShared
import io.opentelemetry.api.logs.Logger
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.common.CompletableResultCode
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@RequiresApi(Build.VERSION_CODES.O)
internal class OneSignalOpenTelemetryRemote(
    private val _configModelStore: ConfigModelStore,
) : IOneSignalOpenTelemetryRemote {
    val extraHttpHeaders by lazy {
        mapOf(
            "OS-App-Id" to _configModelStore.model.appId,
            "x-honeycomb-team" to "", // TODO: REMOVE
        )
    }

    override val logExporter by lazy {
        OtelConfigRemoteOneSignal.HttpRecordBatchExporter.create(extraHttpHeaders)
    }

    private val sdk: OpenTelemetrySdk by lazy {
        OpenTelemetrySdk
            .builder()
            .setLoggerProvider(
                OtelConfigRemoteOneSignal.SdkLoggerProviderConfig.create(
                    OtelConfigShared.ResourceConfig.create(_configModelStore.model),
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

@RequiresApi(Build.VERSION_CODES.O)
internal class OneSignalOpenTelemetryCrashLocal(
    private val _configModelStore: ConfigModelStore,
    private val _crashPathProvider: IOneSignalCrashConfigProvider,
) : IOneSignalOpenTelemetryCrash {
    private val sdk: OpenTelemetrySdk by lazy {
        OpenTelemetrySdk
            .builder()
            .setLoggerProvider(
                OtelConfigCrashFile.SdkLoggerProviderConfig.create(
                    OtelConfigShared.ResourceConfig.create(_configModelStore.model),
                    _crashPathProvider.path,
                    _crashPathProvider.minFileAgeForReadMillis,
                )
            )
            .build()
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
