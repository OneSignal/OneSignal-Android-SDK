package com.onesignal.onesignal.core.internal.outcomes

interface IOutcomeEventsFactory {
    fun getRepository(): IOutcomeEventsRepository
}