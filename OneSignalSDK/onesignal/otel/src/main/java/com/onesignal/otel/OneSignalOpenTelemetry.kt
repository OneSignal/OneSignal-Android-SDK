package com.onesignal.otel

import com.onesignal.otel.attributes.OtelFieldsPerEvent
import com.onesignal.otel.attributes.OtelFieldsTopLevel
import com.onesignal.otel.config.OtelConfigCrashFile
import com.onesignal.otel.config.OtelConfigRemoteOneSignal
import com.onesignal.otel.config.OtelConfigShared
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

/**
 * Extension function to set all attributes from an Attributes object.
 * Made public so it can be used from other modules (e.g., core module for logging).
 */
fun LogRecordBuilder.setAllAttributes(attributes: io.opentelemetry.api.common.Attributes): LogRecordBuilder {
    attributes.forEach { key, value ->
        val keyString = key.key
        when (value) {
            is String -> this.setAttribute(keyString, value)
            is Long -> this.setAttribute(keyString, value)
            is Double -> this.setAttribute(keyString, value)
            is Boolean -> this.setAttribute(keyString, value)
            else -> this.setAttribute(keyString, value.toString())
        }
    }
    return this
}

internal abstract class OneSignalOpenTelemetryBase(
    private val osTopLevelFields: OtelFieldsTopLevel,
    private val osPerEventFields: OtelFieldsPerEvent,
) : IOtelOpenTelemetry {
    private val lock = Any()
    private var sdkCachedValue: OpenTelemetrySdk? = null

    protected suspend fun getSdk(): OpenTelemetrySdk {
        val attributes = osTopLevelFields.getAttributes()
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
                sdkLoggerProvider.forceFlush().join(FORCE_FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun shutdown() {
        synchronized(lock) {
            try {
                sdkCachedValue?.shutdown()
            } catch (_: Throwable) {
                // Best-effort cleanup — never propagate Otel teardown failures
            }
            sdkCachedValue = null
        }
    }

    companion object {
        private const val FORCE_FLUSH_TIMEOUT_SECONDS = 10L
    }

    override suspend fun getLogger(): LogRecordBuilder =
        getSdk()
            .sdkLoggerProvider
            .loggerBuilder("loggerBuilder")
            .build()
            .logRecordBuilder()
            .setAllAttributes(osPerEventFields.getAttributes())
}

internal class OneSignalOpenTelemetryRemote(
    private val platformProvider: IOtelPlatformProvider,
    osTopLevelFields: OtelFieldsTopLevel,
    osPerEventFields: OtelFieldsPerEvent,
) : OneSignalOpenTelemetryBase(osTopLevelFields, osPerEventFields),
    IOtelOpenTelemetryRemote {

    private val appId: String get() = platformProvider.appIdForHeaders

    val extraHttpHeaders: Map<String, String> by lazy {
        mapOf(
            "X-OneSignal-App-Id" to appId,
            "X-OneSignal-SDK-Version" to platformProvider.sdkBaseVersion,
        )
    }

    override val logExporter by lazy {
        OtelConfigRemoteOneSignal.HttpRecordBatchExporter.create(extraHttpHeaders, appId)
    }

    override fun getSdkInstance(attributes: Map<String, String>): OpenTelemetrySdk =
        OpenTelemetrySdk
            .builder()
            .setLoggerProvider(
                OtelConfigRemoteOneSignal.SdkLoggerProviderConfig.create(
                    OtelConfigShared.ResourceConfig.create(attributes),
                    extraHttpHeaders,
                    appId
                )
            ).build()
}

internal class OneSignalOpenTelemetryCrashLocal(
    private val platformProvider: IOtelPlatformProvider,
    osTopLevelFields: OtelFieldsTopLevel,
    osPerEventFields: OtelFieldsPerEvent,
) : OneSignalOpenTelemetryBase(osTopLevelFields, osPerEventFields),
    IOtelOpenTelemetryCrash {
    override fun getSdkInstance(attributes: Map<String, String>): OpenTelemetrySdk =
        OpenTelemetrySdk
            .builder()
            .setLoggerProvider(
                OtelConfigCrashFile.SdkLoggerProviderConfig.create(
                    OtelConfigShared.ResourceConfig.create(
                        attributes
                    ),
                    platformProvider.crashStoragePath,
                    platformProvider.minFileAgeForReadMillis,
                )
            ).build()
}
