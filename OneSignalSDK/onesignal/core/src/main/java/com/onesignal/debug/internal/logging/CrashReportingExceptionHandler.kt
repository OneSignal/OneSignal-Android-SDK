package com.onesignal.debug.internal.logging

internal class CrashReportingExceptionHandler(
    private val crashProcessor: (details: CrashDetails) -> Unit,
    private val postCrashAction: () -> Unit,
    private val existingHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler(),
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        crashProcessor(CrashDetails(thread, throwable))

        // do our best to make sure the crash makes it out of the VM
        postCrashAction()

        // preserve any existing behavior
        existingHandler?.uncaughtException(thread, throwable)
    }
}