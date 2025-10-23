package com.onesignal.debug.internal.logging

import io.opentelemetry.android.instrumentation.common.EventAttributesExtractor
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.OpenTelemetrySdk
//import io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE
//import io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE
//import io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE
//import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_ID
//import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes.THREAD_NAME
import java.util.concurrent.TimeUnit

internal class CrashReporter(
    additionalExtractors: List<EventAttributesExtractor<CrashDetails>>,
) {
    private val extractors: List<EventAttributesExtractor<CrashDetails>> =
        additionalExtractors.toList()

    /** Installs the crash reporting instrumentation.  */
    fun install(openTelemetry: OpenTelemetrySdk) {
        val handler =
            CrashReportingExceptionHandler(
                crashProcessor = { crashDetails: CrashDetails ->
                    processCrash(openTelemetry, crashDetails)
                },
                postCrashAction = {
                    waitForCrashFlush(openTelemetry)
                },
            )
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }

    private fun processCrash(
        openTelemetry: OpenTelemetrySdk,
        crashDetails: CrashDetails,
    ) {
        val logger = openTelemetry.sdkLoggerProvider.loggerBuilder("io.opentelemetry.crash").build()
        val throwable = crashDetails.cause
        val thread = crashDetails.thread
        val attributesBuilder =
            Attributes
                .builder()
//                .put(THREAD_ID, thread.id)
//                .put(THREAD_NAME, thread.name)
//                .put(EXCEPTION_MESSAGE, throwable.message)
//                .put(
//                    EXCEPTION_STACKTRACE,
//                    throwable.stackTraceToString(),
//                ).put(EXCEPTION_TYPE, throwable.javaClass.name)
        for (extractor in extractors) {
            val extractedAttributes = extractor.extract(Context.current(), crashDetails)
            attributesBuilder.putAll(extractedAttributes)
        }
        val eventBuilder =
            logger.logRecordBuilder()
        eventBuilder
            .setEventName("device.crash")
            .setAllAttributes(attributesBuilder.build())
            .emit()
    }

    private fun waitForCrashFlush(openTelemetry: OpenTelemetrySdk) {
        val flushResult = openTelemetry.sdkLoggerProvider.forceFlush()
        flushResult.join(10, TimeUnit.SECONDS)
    }
}
