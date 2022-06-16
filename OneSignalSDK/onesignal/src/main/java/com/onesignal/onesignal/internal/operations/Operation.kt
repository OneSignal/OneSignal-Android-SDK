package com.onesignal.onesignal.internal.operations

abstract class Operation(val name: String)  {
    abstract suspend fun executeAsync()
}