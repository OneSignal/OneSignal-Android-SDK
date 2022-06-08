package com.onesignal.onesignal.internal.operations

interface IOperationRepo  {
    fun enqueue(operation: Operation, force: Boolean = false)
}
