package com.onesignal.otel

import com.onesignal.otel.attributes.OtelFieldsPerEvent
import com.onesignal.otel.attributes.OtelFieldsTopLevel
import com.onesignal.otel.crash.OtelCrashHandler
import com.onesignal.otel.crash.OtelCrashReporter
import com.onesignal.otel.crash.OtelCrashUploader

/**
 * Factory class for creating Otel components.
 * This allows for fast initialization of the crash handler with all dependencies
 * pre-populated.
 */
object OtelFactory {
    /**
     * Creates a fully configured crash handler that can be initialized immediately.
     * All fields are pre-populated for fast initialization.
     *
     * This method composes other factory methods to create the crash handler,
     * ensuring consistency and reducing duplication.
     */
    fun createCrashHandler(
        platformProvider: IOtelPlatformProvider,
        logger: IOtelLogger,
    ): IOtelCrashHandler {
        val crashLocal = createCrashLocalTelemetry(platformProvider)
        val crashReporter = createCrashReporter(crashLocal, logger)
        return OtelCrashHandler(crashReporter, logger)
    }

    /**
     * Creates a crash uploader for sending crash reports to the server.
     *
     * This is platform-agnostic and can be used in KMP projects.
     * All platform-specific values must be provided through IOtelPlatformProvider.
     *
     * @param platformProvider Platform-specific provider that injects all required values
     * @param logger Platform-specific logger implementation
     * @return Platform-agnostic crash uploader that can be used on Android/iOS
     */
    fun createCrashUploader(
        platformProvider: IOtelPlatformProvider,
        logger: IOtelLogger,
    ): OtelCrashUploader {
        val topLevelFields = OtelFieldsTopLevel(platformProvider)
        val perEventFields = OtelFieldsPerEvent(platformProvider)
        val remote = OneSignalOpenTelemetryRemote(
            platformProvider,
            topLevelFields,
            perEventFields
        )
        return OtelCrashUploader(remote, platformProvider, logger)
    }

    /**
     * Creates a remote OpenTelemetry instance for logging SDK events.
     *
     * This is platform-agnostic and can be used in KMP projects.
     * All platform-specific values must be provided through IOtelPlatformProvider.
     *
     * @param platformProvider Platform-specific provider that injects all required values
     * @return Platform-agnostic remote telemetry instance for logging
     */
    fun createRemoteTelemetry(
        platformProvider: IOtelPlatformProvider,
    ): IOtelOpenTelemetryRemote {
        val topLevelFields = OtelFieldsTopLevel(platformProvider)
        val perEventFields = OtelFieldsPerEvent(platformProvider)
        return OneSignalOpenTelemetryRemote(
            platformProvider,
            topLevelFields,
            perEventFields
        )
    }

    /**
     * Creates a local OpenTelemetry crash instance for saving crash reports locally.
     *
     * This is platform-agnostic and can be used in KMP projects.
     * All platform-specific values must be provided through IOtelPlatformProvider.
     *
     * @param platformProvider Platform-specific provider that injects all required values
     * @return Platform-agnostic crash local telemetry instance
     */
    fun createCrashLocalTelemetry(
        platformProvider: IOtelPlatformProvider,
    ): IOtelOpenTelemetryCrash {
        val topLevelFields = OtelFieldsTopLevel(platformProvider)
        val perEventFields = OtelFieldsPerEvent(platformProvider)
        return OneSignalOpenTelemetryCrashLocal(
            platformProvider,
            topLevelFields,
            perEventFields
        )
    }

    /**
     * Creates a crash reporter for saving crash reports.
     *
     * This is platform-agnostic and can be used in KMP projects.
     *
     * @param openTelemetryCrash The crash telemetry instance to use
     * @param logger Platform-specific logger implementation
     * @return Platform-agnostic crash reporter
     */
    fun createCrashReporter(
        openTelemetryCrash: IOtelOpenTelemetryCrash,
        logger: IOtelLogger,
    ): IOtelCrashReporter {
        return OtelCrashReporter(openTelemetryCrash, logger)
    }
}
