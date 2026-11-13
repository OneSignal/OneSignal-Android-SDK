package com.onesignal.common

import android.content.Context

/**
 * The process's application Context, noted by SDK entry points, so code that runs before init
 * (or before ApplicationService starts) can read app storage without a Context of its own.
 */
internal object AppContext {
    @Volatile
    private var context: Context? = null

    fun get(): Context? = context

    /** First caller wins. Only the application Context is kept, so an Activity never leaks here. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun note(context: Context) {
        if (this.context != null) return
        this.context =
            try {
                context.applicationContext
            } catch (_: Throwable) {
                null
            }
    }

    internal fun resetForTest() {
        context = null
    }
}
