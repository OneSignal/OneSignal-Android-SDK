package com.onesignal.logger

import com.onesignal.logger.attributes.LogFieldsPerEvent
import com.onesignal.logger.attributes.LogFieldsTopLevel
import com.onesignal.logger.crash.LogCrashReporter
import com.onesignal.logger.crash.LogCrashUploader
import com.onesignal.logger.internal.LogTelemetryCrashImpl
import com.onesignal.logger.internal.LogTelemetryRemoteImpl

/**
 * Composition root for the logger module. Mirrors `OtelFactory`.
 *
 * Every dependency is injected (platform provider, HTTP sender, file store, logger),
 * keeping the module free of any platform, networking, or storage coupling. This is
 * the "decoupled but not so easy to use" trade-off — callers must supply the platform
 * pieces, but in return the whole pipeline is multiplatform and testable.
 */
object LoggerFactory {
    /**
     * Creates a remote telemetry instance for shipping SDK log events over the
     * network (batched, OTLP/JSON).
     */
    fun createRemoteTelemetry(
        platformProvider: ILoggerPlatformProvider,
        httpSender: ILogHttpSender,
    ): ILogTelemetryRemote =
        LogTelemetryRemoteImpl(
            platformProvider = platformProvider,
            httpSender = httpSender,
            topLevelFields = LogFieldsTopLevel(platformProvider),
            perEventFields = LogFieldsPerEvent(platformProvider),
        )

    /**
     * Creates a crash telemetry instance that persists records to local storage so
     * they survive process death.
     */
    fun createCrashLocalTelemetry(
        platformProvider: ILoggerPlatformProvider,
        fileStore: ILogFileStore,
    ): ILogTelemetryCrash =
        LogTelemetryCrashImpl(
            fileStore = fileStore,
            topLevelFields = LogFieldsTopLevel(platformProvider),
            perEventFields = LogFieldsPerEvent(platformProvider),
        )

    /** Creates a crash reporter that writes a captured crash to [crashTelemetry]. */
    fun createCrashReporter(
        crashTelemetry: ILogTelemetryCrash,
        logger: ILogger,
    ): ILogCrashReporter = LogCrashReporter(crashTelemetry, logger)

    /**
     * Creates a crash uploader that ships disk-buffered crash reports on next launch.
     */
    fun createCrashUploader(
        platformProvider: ILoggerPlatformProvider,
        remote: ILogTelemetryRemote,
        fileStore: ILogFileStore,
        logger: ILogger,
    ): LogCrashUploader = LogCrashUploader(platformProvider, remote, fileStore, logger)
}
