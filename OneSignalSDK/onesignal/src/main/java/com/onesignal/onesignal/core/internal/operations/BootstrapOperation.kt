package com.onesignal.onesignal.core.internal.operations

import com.onesignal.onesignal.core.internal.operations.executors.BootstrapExecutor

class BootstrapOperation(
    val appId: String,
    val subscriptionId: String? = null
) : Operation(BootstrapExecutor.BOOTSTRAP)  {
}