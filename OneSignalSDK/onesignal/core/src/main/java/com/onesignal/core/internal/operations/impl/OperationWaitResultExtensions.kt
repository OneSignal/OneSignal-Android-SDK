package com.onesignal.core.internal.operations.impl

import com.onesignal.core.internal.operations.ExecutionResponse
import com.onesignal.core.internal.operations.OperationWaitResult

internal fun ExecutionResponse?.toWaitResult(success: Boolean): OperationWaitResult =
    OperationWaitResult(success, this?.httpStatusCode, this?.httpResponse, this?.metadata)
