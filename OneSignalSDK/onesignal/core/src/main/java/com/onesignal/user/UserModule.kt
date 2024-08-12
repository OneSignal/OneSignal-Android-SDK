package com.onesignal.user

import com.onesignal.common.IConsistencyManager
import com.onesignal.common.modules.IModule
import com.onesignal.common.services.ServiceBuilder
import com.onesignal.core.internal.operations.IOperationExecutor
import com.onesignal.core.internal.startup.IBootstrapService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.user.internal.UserManager
import com.onesignal.user.internal.backend.IIdentityBackendService
import com.onesignal.user.internal.backend.ISubscriptionBackendService
import com.onesignal.user.internal.backend.IUserBackendService
import com.onesignal.user.internal.backend.impl.IdentityBackendService
import com.onesignal.user.internal.backend.impl.SubscriptionBackendService
import com.onesignal.user.internal.backend.impl.UserBackendService
import com.onesignal.user.internal.builduser.IRebuildUserService
import com.onesignal.user.internal.builduser.impl.RebuildUserService
import com.onesignal.user.internal.identity.IdentityModelStore
import com.onesignal.user.internal.migrations.RecoverFromDroppedLoginBug
import com.onesignal.user.internal.operations.impl.executors.IdentityOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.LoginUserFromSubscriptionOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.LoginUserOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.RefreshUserOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.SubscriptionOperationExecutor
import com.onesignal.user.internal.operations.impl.executors.UpdateUserOperationExecutor
import com.onesignal.user.internal.operations.impl.listeners.IdentityModelStoreListener
import com.onesignal.user.internal.operations.impl.listeners.PropertiesModelStoreListener
import com.onesignal.user.internal.operations.impl.listeners.SubscriptionModelStoreListener
import com.onesignal.user.internal.operations.impl.states.NewRecordsState
import com.onesignal.user.internal.properties.PropertiesModelStore
import com.onesignal.user.internal.service.UserRefreshService
import com.onesignal.user.internal.subscriptions.ISubscriptionManager
import com.onesignal.user.internal.subscriptions.SubscriptionModelStore
import com.onesignal.user.internal.subscriptions.impl.SubscriptionManager

internal class UserModule : IModule {
    override fun register(builder: ServiceBuilder) {
        // Consistency
        builder.register<IConsistencyManager>().provides<IConsistencyManager>()

        // Properties
        builder.register<PropertiesModelStore>().provides<PropertiesModelStore>()
        builder.register<PropertiesModelStoreListener>().provides<IBootstrapService>()

        // Identity
        builder.register<IdentityModelStore>().provides<IdentityModelStore>()
        builder.register<IdentityModelStoreListener>().provides<IBootstrapService>()
        builder.register<IdentityBackendService>().provides<IIdentityBackendService>()
        builder.register<IdentityOperationExecutor>()
            .provides<IdentityOperationExecutor>()
            .provides<IOperationExecutor>()

        // Subscriptions
        builder.register<SubscriptionModelStore>().provides<SubscriptionModelStore>()
        builder.register<SubscriptionModelStoreListener>().provides<IBootstrapService>()
        builder.register<SubscriptionBackendService>().provides<ISubscriptionBackendService>()
        builder.register<SubscriptionOperationExecutor>()
            .provides<SubscriptionOperationExecutor>()
            .provides<IOperationExecutor>()
        builder.register<SubscriptionManager>().provides<ISubscriptionManager>()

        // User
        builder.register<RebuildUserService>().provides<IRebuildUserService>()
        builder.register<UserBackendService>().provides<IUserBackendService>()
        builder.register<UpdateUserOperationExecutor>()
            .provides<UpdateUserOperationExecutor>()
            .provides<IOperationExecutor>()
        builder.register<LoginUserOperationExecutor>().provides<IOperationExecutor>()
        builder.register<LoginUserFromSubscriptionOperationExecutor>().provides<IOperationExecutor>()
        builder.register<RefreshUserOperationExecutor>().provides<IOperationExecutor>()
        builder.register<UserManager>().provides<IUserManager>()

        builder.register<UserRefreshService>().provides<IStartableService>()

        builder.register<RecoverFromDroppedLoginBug>().provides<IStartableService>()

        // Shared state between Executors
        builder.register<NewRecordsState>().provides<NewRecordsState>()
    }
}
