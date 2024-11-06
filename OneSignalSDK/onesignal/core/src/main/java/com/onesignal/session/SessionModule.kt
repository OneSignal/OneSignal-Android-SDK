package com.onesignal.session

import com.onesignal.common.modules.IModule
import com.onesignal.common.services.ServiceBuilder
import com.onesignal.core.internal.background.IBackgroundService
import com.onesignal.core.internal.startup.IBootstrapService
import com.onesignal.core.internal.startup.IStartableService
import com.onesignal.session.internal.SessionManager
import com.onesignal.session.internal.influence.IInfluenceManager
import com.onesignal.session.internal.influence.impl.InfluenceManager
import com.onesignal.session.internal.outcomes.IOutcomeEventsController
import com.onesignal.session.internal.outcomes.impl.IOutcomeEventsBackendService
import com.onesignal.session.internal.outcomes.impl.IOutcomeEventsPreferences
import com.onesignal.session.internal.outcomes.impl.IOutcomeEventsRepository
import com.onesignal.session.internal.outcomes.impl.OutcomeEventsBackendService
import com.onesignal.session.internal.outcomes.impl.OutcomeEventsController
import com.onesignal.session.internal.outcomes.impl.OutcomeEventsPreferences
import com.onesignal.session.internal.outcomes.impl.OutcomeEventsRepository
import com.onesignal.session.internal.session.ISessionService
import com.onesignal.session.internal.session.SessionModelStore
import com.onesignal.session.internal.session.impl.SessionListener
import com.onesignal.session.internal.session.impl.SessionService

internal class SessionModule : IModule {
    override fun register(builder: ServiceBuilder) {
        // Outcomes
        builder.register<OutcomeEventsPreferences>().provides<IOutcomeEventsPreferences>()
        builder.register<OutcomeEventsRepository>().provides<IOutcomeEventsRepository>()
        builder.register<OutcomeEventsBackendService>().provides<IOutcomeEventsBackendService>()
        builder.register<OutcomeEventsController>()
            .provides<IOutcomeEventsController>()
            .provides<IStartableService>()

        // Influence
        builder.register<InfluenceManager>().provides<IInfluenceManager>()

        // Session
        builder.register<SessionModelStore>().provides<SessionModelStore>()
        builder.register<SessionService>()
            .provides<ISessionService>()
            .provides<IStartableService>()
            .provides<IBackgroundService>()
            .provides<IBootstrapService>()
        builder.register<SessionListener>().provides<IStartableService>()
        builder.register<SessionManager>().provides<ISessionManager>()
    }
}
