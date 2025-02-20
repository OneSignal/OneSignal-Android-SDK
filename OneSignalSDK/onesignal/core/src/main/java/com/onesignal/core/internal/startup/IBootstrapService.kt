package com.onesignal.core.internal.startup

import com.onesignal.OneSignal
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModel
import com.onesignal.core.internal.config.ConfigModelStore

/**
 * Implement and provide this interface as part of service registration to indicate the service
 * wants to be instantiated and its [bootstrap] function called during the initialization process.
 *
 * When [IBootstrapService.bootstrap] is called, only [OneSignal.setAppId] have been called during
 * [OneSignal.initWithContext].
 *
 * This means the following is true:
 *
 *  1) An appContext is available in [IApplicationService.appContext].
 *  2) An appId is available in [ConfigModel.appId] via [ConfigModelStore.get]
 *  3) None of the [IStartableService.start] is called
 *
 * When bootstrap there is no guarantee that any other data is available. Typically a bootstrap service
 * must be instantiated immediately and will add their appropriate hooks to then respond to changes
 * in the system.
 */
interface IBootstrapService {
    /**
     * Called when the service is to be bootstrap.  The appId and appContext have already been established.
     */

    fun bootstrap()
}
