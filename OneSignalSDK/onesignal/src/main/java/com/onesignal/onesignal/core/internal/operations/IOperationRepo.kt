package com.onesignal.onesignal.core.internal.operations

interface IOperationRepo  {
    fun enqueue(operation: Operation, force: Boolean = false)
}
