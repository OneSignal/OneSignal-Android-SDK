package com.onesignal.core.internal.operations

import com.onesignal.core.internal.operations.executors.PropertyOperationExecutor

class UpdatePropertyOperation(
    val id: String,
    val property: String,
    val value: Any?
) : Operation(PropertyOperationExecutor.UPDATE_PROPERTY)
