package com.onesignal.onesignal.notification.internal

import com.onesignal.onesignal.core.internal.application.IApplicationService
import com.onesignal.onesignal.core.internal.device.IDeviceService
import com.onesignal.onesignal.core.internal.params.IParamsService
import com.onesignal.onesignal.core.internal.preferences.IPreferencesService
import com.onesignal.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.onesignal.core.internal.startup.IStartableService
import com.onesignal.onesignal.core.internal.time.ITime
import com.onesignal.onesignal.notification.INotificationsManager
import com.onesignal.onesignal.notification.internal.analytics.IAnalyticsTracker
import com.onesignal.onesignal.notification.internal.analytics.impl.FirebaseAnalyticsTracker
import com.onesignal.onesignal.notification.internal.analytics.impl.NoAnalyticsTracker
import com.onesignal.onesignal.notification.internal.backend.INotificationBackendService
import com.onesignal.onesignal.notification.internal.backend.impl.NotificationBackendService
import com.onesignal.onesignal.notification.internal.badges.IBadgeCountUpdater
import com.onesignal.onesignal.notification.internal.badges.impl.BadgeCountUpdater
import com.onesignal.onesignal.notification.internal.bundle.INotificationBundleProcessor
import com.onesignal.onesignal.notification.internal.bundle.impl.NotificationBundleProcessor
import com.onesignal.onesignal.notification.internal.channels.INotificationChannelManager
import com.onesignal.onesignal.notification.internal.channels.impl.NotificationChannelManager
import com.onesignal.onesignal.notification.internal.data.INotificationDataController
import com.onesignal.onesignal.notification.internal.data.INotificationQueryHelper
import com.onesignal.onesignal.notification.internal.data.impl.NotificationDataController
import com.onesignal.onesignal.notification.internal.data.impl.NotificationQueryHelper
import com.onesignal.onesignal.notification.internal.display.INotificationDisplayBuilder
import com.onesignal.onesignal.notification.internal.display.INotificationDisplayer
import com.onesignal.onesignal.notification.internal.display.ISummaryNotificationDisplayer
import com.onesignal.onesignal.notification.internal.display.impl.NotificationDisplayBuilder
import com.onesignal.onesignal.notification.internal.display.impl.NotificationDisplayer
import com.onesignal.onesignal.notification.internal.display.impl.SummaryNotificationDisplayer
import com.onesignal.onesignal.notification.internal.generation.INotificationGenerationProcessor
import com.onesignal.onesignal.notification.internal.generation.INotificationGenerationWorkManager
import com.onesignal.onesignal.notification.internal.generation.impl.NotificationGenerationProcessor
import com.onesignal.onesignal.notification.internal.generation.impl.NotificationGenerationWorkManager
import com.onesignal.onesignal.notification.internal.lifecycle.INotificationLifecycleService
import com.onesignal.onesignal.notification.internal.lifecycle.impl.NotificationLifecycleService
import com.onesignal.onesignal.notification.internal.limiting.INotificationLimitManager
import com.onesignal.onesignal.notification.internal.limiting.impl.NotificationLimitManager
import com.onesignal.onesignal.notification.internal.listeners.ApplicationListener
import com.onesignal.onesignal.notification.internal.listeners.NotificationListener
import com.onesignal.onesignal.notification.internal.listeners.ParamsListener
import com.onesignal.onesignal.notification.internal.listeners.PushTokenListener
import com.onesignal.onesignal.notification.internal.open.INotificationOpenedProcessor
import com.onesignal.onesignal.notification.internal.open.INotificationOpenedProcessorHMS
import com.onesignal.onesignal.notification.internal.open.impl.NotificationOpenedProcessor
import com.onesignal.onesignal.notification.internal.open.impl.NotificationOpenedProcessorHMS
import com.onesignal.onesignal.notification.internal.permissions.INotificationPermissionController
import com.onesignal.onesignal.notification.internal.permissions.impl.NotificationPermissionController
import com.onesignal.onesignal.notification.internal.pushtoken.IPushTokenManager
import com.onesignal.onesignal.notification.internal.pushtoken.PushTokenManager
import com.onesignal.onesignal.notification.internal.receivereceipt.IReceiveReceiptProcessor
import com.onesignal.onesignal.notification.internal.receivereceipt.IReceiveReceiptWorkManager
import com.onesignal.onesignal.notification.internal.receivereceipt.impl.ReceiveReceiptProcessor
import com.onesignal.onesignal.notification.internal.receivereceipt.impl.ReceiveReceiptWorkManager
import com.onesignal.onesignal.notification.internal.registration.IPushRegistrator
import com.onesignal.onesignal.notification.internal.registration.impl.GooglePlayServicesUpgradePrompt
import com.onesignal.onesignal.notification.internal.registration.impl.IPushRegistratorCallback
import com.onesignal.onesignal.notification.internal.registration.impl.PushRegistratorADM
import com.onesignal.onesignal.notification.internal.registration.impl.PushRegistratorFCM
import com.onesignal.onesignal.notification.internal.registration.impl.PushRegistratorHMS
import com.onesignal.onesignal.notification.internal.registration.impl.PushRegistratorNone
import com.onesignal.onesignal.notification.internal.restoration.INotificationRestoreProcessor
import com.onesignal.onesignal.notification.internal.restoration.INotificationRestoreWorkManager
import com.onesignal.onesignal.notification.internal.restoration.impl.NotificationRestoreProcessor
import com.onesignal.onesignal.notification.internal.restoration.impl.NotificationRestoreWorkManager
import com.onesignal.onesignal.notification.internal.summary.INotificationSummaryManager
import com.onesignal.onesignal.notification.internal.summary.impl.NotificationSummaryManager

object NotificationModule {
    fun register(builder: ServiceBuilder) {
        builder.register<NotificationBackendService>().provides<INotificationBackendService>()

        builder.register<NotificationRestoreWorkManager>().provides<INotificationRestoreWorkManager>()
        builder.register<NotificationQueryHelper>().provides<INotificationQueryHelper>()
        builder.register<BadgeCountUpdater>().provides<IBadgeCountUpdater>()
        builder.register<NotificationDataController>().provides<INotificationDataController>()
        builder.register<NotificationGenerationWorkManager>().provides<INotificationGenerationWorkManager>()
        builder.register<NotificationBundleProcessor>().provides<INotificationBundleProcessor>()
        builder.register<NotificationChannelManager>().provides<INotificationChannelManager>()
        builder.register<NotificationLimitManager>().provides<INotificationLimitManager>()

        builder.register<NotificationDisplayer>().provides<INotificationDisplayer>()
        builder.register<SummaryNotificationDisplayer>().provides<ISummaryNotificationDisplayer>()
        builder.register<NotificationDisplayBuilder>().provides<INotificationDisplayBuilder>()

        builder.register<NotificationGenerationProcessor>().provides<INotificationGenerationProcessor>()
        builder.register<NotificationRestoreProcessor>().provides<INotificationRestoreProcessor>()
        builder.register<NotificationSummaryManager>().provides<INotificationSummaryManager>()

        builder.register<NotificationOpenedProcessor>().provides<INotificationOpenedProcessor>()
        builder.register<NotificationOpenedProcessorHMS>().provides<INotificationOpenedProcessorHMS>()

        builder.register<NotificationPermissionController>().provides<INotificationPermissionController>()

        builder.register<NotificationLifecycleService>()
            .provides<INotificationLifecycleService>()

        builder.register {
            if (FirebaseAnalyticsTracker.canTrack())
                return@register FirebaseAnalyticsTracker(
                    it.getService(IApplicationService::class.java),
                    it.getService(IPreferencesService::class.java),
                    it.getService(ITime::class.java)
                )
            else
                return@register NoAnalyticsTracker()
        }
            .provides<IAnalyticsTracker>()

        builder.register {
            val deviceService = it.getService(IDeviceService::class.java)
            val service = if (deviceService.isFireOSDeviceType)
                PushRegistratorADM(it.getService(IApplicationService::class.java))
            else if (deviceService.isAndroidDeviceType) {
                if (deviceService.hasFCMLibrary())
                    PushRegistratorFCM(it.getService(IParamsService::class.java), it.getService(IApplicationService::class.java), it.getService(GooglePlayServicesUpgradePrompt::class.java), deviceService)
                else
                    PushRegistratorNone()
            } else {
                PushRegistratorHMS(deviceService, it.getService(IApplicationService::class.java))
            }
            return@register service
        }.provides<IPushRegistrator>()
            .provides<IPushRegistratorCallback>()

        builder.register<GooglePlayServicesUpgradePrompt>().provides<GooglePlayServicesUpgradePrompt>()
        builder.register<PushTokenManager>().provides<IPushTokenManager>()

        builder.register<ReceiveReceiptWorkManager>().provides<IReceiveReceiptWorkManager>()
        builder.register<ReceiveReceiptProcessor>().provides<IReceiveReceiptProcessor>()

        // Startable services
        builder.register<ApplicationListener>().provides<IStartableService>()
        builder.register<ParamsListener>().provides<IStartableService>()
        builder.register<NotificationListener>().provides<IStartableService>()
        builder.register<PushTokenListener>().provides<IStartableService>()

        builder.register<NotificationsManager>()
            .provides<INotificationsManager>()
            .provides<INotificationActivityOpener>()
            .provides<INotificationStateRefresher>()
    }
}
