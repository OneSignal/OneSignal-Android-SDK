package com.onesignal.debug.internal.logging.otel

import io.opentelemetry.api.logs.Logger
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.logs.export.LogRecordExporter

internal interface IOneSignalOpenTelemetry {
    val logger: Logger

    suspend fun forceFlush(): CompletableResultCode
}

internal interface IOneSignalOpenTelemetryCrash : IOneSignalOpenTelemetry

internal interface IOneSignalOpenTelemetryRemote : IOneSignalOpenTelemetry {
    val logExporter: LogRecordExporter
}
