package com.onesignal.core.internal.operations

interface IOperationRepo {
    fun enqueue(operation: Operation, force: Boolean = false)
}
