package com.onesignal.notifications

import com.onesignal.common.modules.IModule
import com.onesignal.common.services.ServiceBuilder
import com.onesignal.core.internal.application.IApplicationService
import com.onesignal.core.internal.config.ConfigModelStore
import com.onesignal.core.internal.device.IDeviceService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.core.internal.time.ITime
import com.onesignal.notifications.internal.INotificationActivityOpener
import com.onesignal.notifications.internal.NotificationsManager
import com.onesignal.notifications.internal.analytics.IAnalyticsTracker
import com.onesignal.notifications.internal.analytics.impl.FirebaseAnalyticsTracker
import com.onesignal.notifications.internal.analytics.impl.NoAnalyticsTracker
import com.onesignal.notifications.internal.backend.INotificationBackendService
import com.onesignal.notifications.internal.backend.impl.NotificationBackendService
import com.onesignal.notifications.internal.badges.IBadgeCountUpdater
import com.onesignal.notifications.internal.badges.impl.BadgeCountUpdater
import com.onesignal.notifications.internal.bundle.INotificationBundleProcessor
import com.onesignal.notifications.internal.bundle.impl.NotificationBundleProcessor
import com.onesignal.notifications.internal.channels.INotificationChannelManager
import com.onesignal.notifications.internal.channels.impl.NotificationChannelManager
import com.onesignal.notifications.internal.data.INotificationQueryHelper
import com.onesignal.notifications.internal.data.INotificationRepository
import com.onesignal.notifications.internal.data.impl.NotificationQueryHelper
import com.onesignal.notifications.internal.data.impl.NotificationRepository
import com.onesignal.notifications.internal.display.INotificationDisplayBuilder
import com.onesignal.notifications.internal.display.INotificationDisplayer
import com.onesignal.notifications.internal.display.ISummaryNotificationDisplayer
import com.onesignal.notifications.internal.display.impl.NotificationDisplayBuilder
import com.onesignal.notifications.internal.display.impl.NotificationDisplayer
import com.onesignal.notifications.internal.display.impl.SummaryNotificationDisplayer
import com.onesignal.notifications.internal.generation.INotificationGenerationProcessor
import com.onesignal.notifications.internal.generation.INotificationGenerationWorkManager
import com.onesignal.notifications.internal.generation.impl.NotificationGenerationProcessor
import com.onesignal.notifications.internal.generation.impl.NotificationGenerationWorkManager
import com.onesignal.notifications.internal.lifecycle.INotificationLifecycleService
import com.onesignal.notifications.internal.lifecycle.impl.NotificationLifecycleService
import com.onesignal.notifications.internal.limiting.INotificationLimitManager
import com.onesignal.notifications.internal.limiting.impl.NotificationLimitManager
import com.onesignal.notifications.internal.listeners.DeviceRegistrationListener
import com.onesignal.notifications.internal.open.INotificationOpenedProcessor
import com.onesignal.notifications.internal.open.INotificationOpenedProcessorHMS
import com.onesignal.notifications.internal.open.impl.NotificationOpenedProcessor
import com.onesignal.notifications.internal.open.impl.NotificationOpenedProcessorHMS
import com.onesignal.notifications.internal.permissions.INotificationPermissionController
import com.onesignal.notifications.internal.permissions.impl.NotificationPermissionController
import com.onesignal.notifications.internal.pushtoken.IPushTokenManager
import com.onesignal.notifications.internal.pushtoken.PushTokenManager
import com.onesignal.notifications.internal.receivereceipt.IReceiveReceiptProcessor
import com.onesignal.notifications.internal.receivereceipt.IReceiveReceiptWorkManager
import com.onesignal.notifications.internal.receivereceipt.impl.ReceiveReceiptProcessor
import com.onesignal.notifications.internal.receivereceipt.impl.ReceiveReceiptWorkManager
import com.onesignal.notifications.internal.registration.IPushRegistrator
import com.onesignal.notifications.internal.registration.impl.GooglePlayServicesUpgradePrompt
import com.onesignal.notifications.internal.registration.impl.IPushRegistratorCallback
import com.onesignal.notifications.internal.registration.impl.PushRegistratorADM
import com.onesignal.notifications.internal.registration.impl.PushRegistratorFCM
import com.onesignal.notifications.internal.registration.impl.PushRegistratorHMS
import com.onesignal.notifications.internal.registration.impl.PushRegistratorNone
import com.onesignal.notifications.internal.restoration.INotificationRestoreProcessor
import com.onesignal.notifications.internal.restoration.INotificationRestoreWorkManager
import com.onesignal.notifications.internal.restoration.impl.NotificationRestoreProcessor
import com.onesignal.notifications.internal.restoration.impl.NotificationRestoreWorkManager
import com.onesignal.notifications.internal.summary.INotificationSummaryManager
import com.onesignal.notifications.internal.summary.impl.NotificationSummaryManager

internal class NotificationsModule : IModule {
    override fun register(builder: ServiceBuilder) {
        builder.register<NotificationBackendService>().provides<INotificationBackendService>()

        builder.register<NotificationRestoreWorkManager>().provides<INotificationRestoreWorkManager>()
        builder.register<NotificationQueryHelper>().provides<INotificationQueryHelper>()
        builder.register<BadgeCountUpdater>().provides<IBadgeCountUpdater>()
        builder.register<NotificationRepository>().provides<INotificationRepository>()
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
            .provides<INotificationActivityOpener>()

        builder.register {
            if (FirebaseAnalyticsTracker.canTrack()) {
                return@register FirebaseAnalyticsTracker(
                    it.getService(IApplicationService::class.java),
                    it.getService(ConfigModelStore::class.java),
                    it.getService(ITime::class.java),
                )
            } else {
                return@register NoAnalyticsTracker()
            }
        }
            .provides<IAnalyticsTracker>()

        builder.register {
            val deviceService = it.getService(IDeviceService::class.java)
            val service =
                if (deviceService.isFireOSDeviceType) {
                    PushRegistratorADM(it.getService(IApplicationService::class.java))
                } else if (deviceService.isAndroidDeviceType) {
                    if (deviceService.hasFCMLibrary) {
                        PushRegistratorFCM(
                            it.getService(ConfigModelStore::class.java),
                            it.getService(
                                IApplicationService::class.java,
                            ),
                            it.getService(GooglePlayServicesUpgradePrompt::class.java),
                            deviceService,
                        )
                    } else {
                        PushRegistratorNone()
                    }
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
        builder.register<DeviceRegistrationListener>().provides<IStartableService>()

        builder.register<NotificationsManager>()
            .provides<INotificationsManager>()
    }
}
