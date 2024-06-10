package com.onesignal.core.internal.application

import android.app.Application.ActivityLifecycleCallbacks

/**
 * Implementations of the application lifecycle handler added via [IApplicationService.addApplicationLifecycleHandler]
 * will be notified throughout the application lifecycle.  This should be used over the Android-provided
 * [ActivityLifecycleCallbacks], it provides an abstraction more specific to the OneSignal SDK
 */
interface IApplicationLifecycleHandler {
    /**
     * Called when the application is brought into the foreground.
     * This callback can be fired immediately on subscribing to the IApplicationService (when the
     * IApplicationService itself is started too late to capture the application's early lifecycle events),
     * or through natural application lifecycle callbacks.
     *
     * @param firedOnSubscribe Method is fired from subscribing or from application lifecycle callbacks
     */
    fun onFocus(firedOnSubscribe: Boolean)

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
    override fun onFocus(firedOnSubscribe: Boolean) {}

    override fun onUnfocused() {}
}
