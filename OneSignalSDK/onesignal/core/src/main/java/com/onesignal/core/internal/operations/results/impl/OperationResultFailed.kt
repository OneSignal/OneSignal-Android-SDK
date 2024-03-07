package com.onesignal.core.internal.operations.results.impl

import com.onesignal.core.internal.operations.results.IOperationResult
import com.onesignal.core.internal.operations.results.ReasonFailed

internal class OperationResultFailed : IOperationResult {
    /**
     * The reason for the operation failure
     */
    val reason: ReasonFailed,
}