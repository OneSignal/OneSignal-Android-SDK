package com.onesignal.core.internal.outcomes

internal interface IOutcomeEventsFactory {
    fun getRepository(): IOutcomeEventsRepository
}
