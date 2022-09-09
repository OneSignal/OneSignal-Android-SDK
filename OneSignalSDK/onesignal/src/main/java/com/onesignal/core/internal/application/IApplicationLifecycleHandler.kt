package com.onesignal.core.internal.application

internal interface IApplicationLifecycleHandler {
    fun onFocus()

    /**
     * Called when the application has been brought out of the foreground, to the background.
     */
    fun onUnfocused()
}

/**
 * A base implementation of [IApplicationLifecycleHandler] that is a no-op. Concrete implementations
 * can use this if they only want to override a subset of the callbacks that make up this interface.
 */
open class ApplicationLifecycleHandlerBase : IApplicationLifecycleHandler {
    override fun onFocus() {}
    override fun onUnfocused() {}
}
