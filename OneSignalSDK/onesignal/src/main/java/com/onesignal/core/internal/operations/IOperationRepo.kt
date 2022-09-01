package com.onesignal.core.internal.operations

internal interface IOperationRepo {
    fun enqueue(operation: Operation, force: Boolean = false)
}
