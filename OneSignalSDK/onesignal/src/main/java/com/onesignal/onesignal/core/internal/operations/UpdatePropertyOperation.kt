package com.onesignal.onesignal.core.internal.operations

import com.onesignal.onesignal.core.internal.operations.impl.executors.PropertyOperationExecutor

class UpdatePropertyOperation(
    val id: String,
    val property: String,
    val value: Any?) : Operation(PropertyOperationExecutor.UPDATE_PROPERTY)  {
}