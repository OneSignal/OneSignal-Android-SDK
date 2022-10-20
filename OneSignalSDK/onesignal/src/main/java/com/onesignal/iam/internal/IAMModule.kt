package com.onesignal.iam.internal

import android.os.Build
import com.onesignal.core.internal.service.ServiceBuilder
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.iam.IIAMManager
import com.onesignal.iam.internal.backend.IInAppBackendService
import com.onesignal.iam.internal.backend.impl.InAppBackendService
import com.onesignal.iam.internal.display.IInAppDisplayer
import com.onesignal.iam.internal.display.impl.InAppDisplayer
import com.onesignal.iam.internal.hydrators.InAppHydrator
import com.onesignal.iam.internal.lifecycle.IInAppLifecycleService
import com.onesignal.iam.internal.lifecycle.impl.IAMLifecycleService
import com.onesignal.iam.internal.preferences.IInAppPreferencesController
import com.onesignal.iam.internal.preferences.impl.InAppPreferencesController
import com.onesignal.iam.internal.preview.InAppMessagePreviewHandler
import com.onesignal.iam.internal.prompt.IInAppMessagePromptFactory
import com.onesignal.iam.internal.prompt.impl.InAppMessagePromptFactory
import com.onesignal.iam.internal.repositories.IInAppRepository
import com.onesignal.iam.internal.repositories.impl.InAppRepository
import com.onesignal.iam.internal.state.InAppStateService
import com.onesignal.iam.internal.triggers.ITriggerController
import com.onesignal.iam.internal.triggers.impl.DynamicTriggerController
import com.onesignal.iam.internal.triggers.impl.TriggerController

internal object IAMModule {
    fun register(builder: ServiceBuilder) {
        // Make sure only Android 4.4 devices and higher can use IAMs
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            builder.register<DummyIAMManager>()
                   .provides<IIAMManager>()
        }
        else {
            // Low level services
            builder.register<InAppStateService>().provides<InAppStateService>()
            builder.register<InAppHydrator>().provides<InAppHydrator>()
            builder.register<InAppPreferencesController>().provides<IInAppPreferencesController>()
            builder.register<InAppRepository>().provides<IInAppRepository>()
            builder.register<InAppBackendService>().provides<IInAppBackendService>()
            builder.register<IAMLifecycleService>().provides<IInAppLifecycleService>()

            // Triggers
            builder.register<TriggerController>().provides<ITriggerController>()
            builder.register<DynamicTriggerController>()
                .provides<DynamicTriggerController>() // observer

            // Display
            builder.register<InAppDisplayer>().provides<IInAppDisplayer>()

            // Previews
            builder.register<InAppMessagePreviewHandler>().provides<IStartableService>()

            // Prompts
            builder.register<InAppMessagePromptFactory>().provides<IInAppMessagePromptFactory>()

            builder.register<IAMManager>()
                .provides<IIAMManager>()
                .provides<IStartableService>()
        }
    }
}
