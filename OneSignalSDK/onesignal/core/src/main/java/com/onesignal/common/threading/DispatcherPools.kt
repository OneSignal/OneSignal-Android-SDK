package com.onesignal.common.threading

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ThreadPoolExecutor

internal interface DispatcherLane {
    val dispatcher: CoroutineDispatcher

    fun executor(): ThreadPoolExecutor?

    fun status(): String
}

internal interface DispatcherPools {
    val io: DispatcherLane
    val defaultLane: DispatcherLane
    val serialIO: DispatcherLane
    val ingress: DispatcherLane
    val ioScope: CoroutineScope
    val defaultScope: CoroutineScope
    val serialIOScope: CoroutineScope
    val ingressScope: CoroutineScope

    fun prewarm()

    fun shutdown()
}
