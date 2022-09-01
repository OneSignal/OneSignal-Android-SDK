package com.onesignal.onesignal.core.internal.startup

import com.onesignal.onesignal.core.OneSignal
import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.models.ConfigModel
import com.onesignal.onesignal.core.internal.models.ConfigModelStore

/**
 * Implement and provide this interface as part of service registration to indicate the service
 * wants to be instantiated and its [start] function called during the initialization process.
 *
 * When [IStartableService.start] is called, both [OneSignal.initWithContext] and [OneSignal.setAppId]
 * have been called.  This means the following is true:
 *
 *  1) An appContext is available in [IApplicationService.appContext].
 *  2) An appId is available in [ConfigModel.appId] via [ConfigModelStore.get]
 *
 * When started there is no guarantee that any other data is available.  Typically a startable service
 * must be instantiated immediately and will add their appropriate hooks to then respond to changes
 * in the system.
 */
interface IStartableService {

    /**
     * Called when the service is to be started.  The appId and appContext have already been
     * established.
     */
    fun start()
}
