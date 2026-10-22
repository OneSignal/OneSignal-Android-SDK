package com.onesignal.common.threading

import java.util.concurrent.ThreadPoolExecutor

/** Seams for dispatcher tests. Lane hooks receive [Lane.name]. Null in production. */
internal object DispatcherTestHooks {
    @Volatile
    var beforeLaneCreate: ((String) -> Unit)? = null

    @Volatile
    var afterExecutorCreate: ((String, ThreadPoolExecutor) -> Unit)? = null

    @Volatile
    var beforeFallbackCreate: ((String) -> Unit)? = null

    /** Lazy prewarm, before the lane lazy runs. Blocking here must not deadlock shutdown. */
    @Volatile
    var beforeLazyWarm: ((String) -> Unit)? = null

    /** After the lazy prewarm thread is published, before [Thread.start]. */
    @Volatile
    var beforeLazyPrewarmStart: (() -> Unit)? = null

    @Volatile
    var beforePoolChoice: (() -> Unit)? = null

    /** Inside the choice report handoff, between reading readiness and parking the report. */
    @Volatile
    var duringChoiceHandoff: (() -> Unit)? = null

    fun reset() {
        beforeLaneCreate = null
        afterExecutorCreate = null
        beforeFallbackCreate = null
        beforeLazyWarm = null
        beforeLazyPrewarmStart = null
        beforePoolChoice = null
        duringChoiceHandoff = null
    }
}
